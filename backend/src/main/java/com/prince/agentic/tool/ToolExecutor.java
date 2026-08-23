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
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The single deterministic entry point for running a tool — the boundary the agent calls.
 *
 * <p>Enforces the non-negotiable ordered gates and always returns a {@link ToolResult} (it does not
 * throw for execution-path outcomes):
 * <pre>
 *   deadline-check → resolve → authenticate → authorize (role) → bind → validate → execute → wrap
 * </pre>
 *
 * <p><b>H-03 two-tier timeout enforcement:</b>
 * <ol>
 *   <li><b>Pre-execution deadline check (all tools):</b> if {@link ToolExecutionContext#deadline()}
 *       is present and already past, the tool is rejected with {@code TOOL_TIMEOUT} before any
 *       execution or side-effect begins.</li>
 *   <li><b>In-flight Future timeout (READ_ONLY/DETERMINISTIC only):</b> execution is wrapped in a
 *       virtual-thread {@link CompletableFuture} with {@code orTimeout} matching the descriptor's
 *       {@link ToolDescriptor#timeout()}. SIDE_EFFECTING and HIGH_RISK tools are never forcibly
 *       interrupted after starting (M8 invariant — see docs/GUARDRAILS.md).</li>
 * </ol>
 *
 * <p><b>Security:</b> identity comes only from {@code context.principal()} (never from arguments);
 * unknown argument properties are rejected (a spoofed {@code ownerId}/{@code userId} → invalid input,
 * loud not silent); resource-ownership authorization is delegated to the domain service the tool wraps.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    // Virtual-thread-per-task executor for safe in-flight timeouts (Java 21).
    private static final Executor VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

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

            // H-03 tier 1: pre-execution deadline check — applies to ALL risk levels.
            // If the agent-level deadline has already passed, reject before any side-effect begins.
            if (context != null && context.deadline().isPresent()
                    && Instant.now().isAfter(context.deadline().get())) {
                return fail(toolName, "TOOL_TIMEOUT", "agent deadline exceeded before tool start",
                        start, risk, context);
            }

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
                return fail(toolName, "TOOL_INVALID_INPUT", "arguments do not match the tool input",
                        start, risk, context);
            }

            Set<ConstraintViolation<Object>> violations = validator.validate(input);
            if (!violations.isEmpty()) {
                return fail(toolName, "TOOL_INVALID_INPUT", firstMessage(violations), start, risk, context);
            }

            // H-03 tier 2: risk-aware execution policy.
            Object data;
            if (d.risk() == ToolRiskLevel.READ_ONLY || d.risk() == ToolRiskLevel.DETERMINISTIC) {
                // Safe to interrupt: wrap in a timed Future so a stuck tool doesn't block indefinitely.
                data = timedExecute(tool, d, context, input);
            } else {
                // SIDE_EFFECTING / HIGH_RISK: never forcibly interrupt after starting (M8 invariant).
                data = ((Tool<Object, Object>) tool).execute(context, input);
            }

            long ms = elapsedMs(start);
            record(toolName, risk, "success", ms, context);
            return ToolResult.ok(toolName, data, ms);

        } catch (ToolTimeoutSignal ts) {
            // In-flight Future timed out (READ_ONLY/DETERMINISTIC only, tier 2).
            return fail(toolName, "TOOL_TIMEOUT", "tool exceeded its time limit", start, risk, context);
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
     * Executes the tool with a descriptor-scoped timeout. Used only for READ_ONLY and DETERMINISTIC tools.
     *
     * <p><b>Transaction context safety:</b> Spring's {@code @Transactional} propagation is
     * {@link ThreadLocal}-based. Spinning the tool off to a virtual thread would sever that context.
     * In production, the {@link com.prince.agentic.agent.AgentOrchestrator} never holds an active
     * transaction across a tool call (see docs/GUARDRAILS.md), so the virtual-thread path is correct
     * there. In {@code @Transactional} test methods, we detect the active transaction and fall back to
     * inline execution so that tests can see uncommitted test-data created on the same thread.
     *
     * <p>Throws {@link ToolTimeoutSignal} (extends {@link RuntimeException}) on timeout so the outer
     * catch in {@link #execute} can distinguish it from unexpected errors.
     */
    @SuppressWarnings("unchecked")
    private Object timedExecute(Tool<?, ?> tool, ToolDescriptor d,
                                ToolExecutionContext context, Object input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Caller has an active transaction (e.g. @Transactional test): stay on the current thread
            // to preserve Spring's ThreadLocal-based propagation. Timeout not enforced in this path —
            // this never happens in production (orchestrator holds no transaction across tool calls).
            return ((Tool<Object, Object>) tool).execute(context, input);
        }
        try {
            return CompletableFuture.supplyAsync(
                            () -> ((Tool<Object, Object>) tool).execute(context, input),
                            VIRTUAL_EXECUTOR)
                    .orTimeout(d.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof TimeoutException) {
                throw new ToolTimeoutSignal();
            }
            if (cause instanceof ApiException ae) throw ae;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    /** Internal signal (never escapes ToolExecutor) that a timed Future expired. */
    private static final class ToolTimeoutSignal extends RuntimeException {
        ToolTimeoutSignal() {
            super("tool timeout signal", null, true, false); // no stack trace — never logged
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
