package com.prince.agentic.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.exception.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * The single deterministic entry point for running a tool — the boundary the future agent (M6) calls.
 *
 * <p>Enforces the non-negotiable ordered gates and always returns a {@link ToolResult} (it does not
 * throw for execution-path outcomes):
 * <pre>
 *   resolve → authenticate → authorize (role) → bind → validate → execute → wrap
 * </pre>
 *
 * <p><b>Security:</b> identity comes only from {@code context.principal()} (never from arguments);
 * unknown argument properties are rejected (a spoofed {@code ownerId}/{@code userId} → invalid input,
 * loud not silent); resource-ownership authorization is delegated to the domain service the tool wraps.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MeterRegistry meters;

    public ToolExecutor(ToolRegistry registry, ObjectMapper objectMapper,
                        Validator validator, MeterRegistry meters) {
        this.registry = registry;
        // A private, strict copy: unknown properties (e.g. a spoofed ownerId) are rejected, not ignored.
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.validator = validator;
        this.meters = meters;
    }

    @SuppressWarnings("unchecked")
    public ToolResult<Object> execute(String toolName, Map<String, Object> rawArguments,
                                      ToolExecutionContext context) {
        long start = System.nanoTime();
        String risk = "unknown";
        try {
            Tool<?, ?> tool = registry.resolve(toolName);
            if (tool == null) {
                return fail(toolName, "TOOL_NOT_FOUND", "unknown tool: " + toolName, start, risk, context);
            }
            ToolDescriptor d = tool.descriptor();
            risk = d.risk().name();

            if (d.requiresAuthentication() && (context == null || context.principal() == null)) {
                return fail(toolName, "TOOL_UNAUTHORIZED", "authentication required", start, risk, context);
            }
            if (!hasRoles(context, d.requiredRoles())) {
                return fail(toolName, "TOOL_FORBIDDEN", "missing required role", start, risk, context);
            }

            Object input;
            try {
                input = objectMapper.convertValue(
                        rawArguments == null ? Map.of() : rawArguments, d.inputType());
            } catch (IllegalArgumentException e) {
                return fail(toolName, "TOOL_INVALID_INPUT", "arguments do not match the tool input", start, risk, context);
            }

            Set<ConstraintViolation<Object>> violations = validator.validate(input);
            if (!violations.isEmpty()) {
                return fail(toolName, "TOOL_INVALID_INPUT", firstMessage(violations), start, risk, context);
            }

            Object data = ((Tool<Object, Object>) tool).execute(context, input);
            long ms = elapsedMs(start);
            record(toolName, risk, "success", ms, context);
            return ToolResult.ok(toolName, data, ms);

        } catch (ApiException domain) {
            // Domain (or tool) ApiException — surface its stable code/message as the observation
            // (e.g. NOT_FOUND / FORBIDDEN from a domain service, or a TOOL_* from the tool itself).
            return fail(toolName, domain.getCode(), domain.getMessage(), start, risk, context);
        } catch (RuntimeException unexpected) {
            log.warn("tool.exec unexpected failure tool={} risk={}", toolName, risk, unexpected);
            return fail(toolName, "TOOL_EXECUTION_FAILED", "tool execution failed", start, risk, context);
        }
    }

    /**
     * Tool-type (role) authorization with <b>any-of</b> semantics (like Spring's {@code hasAnyRole}):
     * an empty requirement allows any authenticated principal; otherwise the principal must hold at
     * least one of the required roles. Shared domain tools declare {@code {ROLE_USER, ROLE_ADMIN}} so
     * both a normal user and an admin pass, while resource ownership is decided by the domain service.
     */
    private boolean hasRoles(ToolExecutionContext ctx, Set<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (ctx == null || ctx.principal() == null) {
            return false;
        }
        return ctx.principal().roles().stream().anyMatch(required::contains);
    }

    private ToolResult<Object> fail(String tool, String code, String message,
                                    long start, String risk, ToolExecutionContext ctx) {
        long ms = elapsedMs(start);
        record(tool, risk, "failure", ms, ctx);
        return ToolResult.failure(tool, new ToolError(code, message), ms);
    }

    private void record(String tool, String risk, String outcome, long ms, ToolExecutionContext ctx) {
        meters.timer("tool.execution.duration", "tool", tool, "risk", risk, "outcome", outcome)
                .record(Duration.ofMillis(ms));
        meters.counter("tool.execution.result", "tool", tool, "risk", risk, "outcome", outcome).increment();
        Long uid = (ctx == null || ctx.principal() == null) ? null : ctx.principal().userId();
        log.info("tool.exec tool={} risk={} outcome={} durationMs={} user={}", tool, risk, outcome, ms, uid);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String firstMessage(Set<ConstraintViolation<Object>> violations) {
        ConstraintViolation<Object> v = violations.iterator().next();
        return v.getPropertyPath() + " " + v.getMessage();
    }
}
