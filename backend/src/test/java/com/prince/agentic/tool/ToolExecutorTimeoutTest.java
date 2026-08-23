package com.prince.agentic.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-03 tool-timeout enforcement tests.
 *
 * <p>Two-tier policy:
 * <ol>
 *   <li><b>Pre-execution deadline check</b> — if the agent-level deadline has already passed before
 *       the tool starts, all risk levels are rejected with TOOL_TIMEOUT.</li>
 *   <li><b>In-flight Future timeout</b> — only READ_ONLY and DETERMINISTIC tools are wrapped in a
 *       {@link java.util.concurrent.CompletableFuture} with {@code orTimeout}. SIDE_EFFECTING and
 *       HIGH_RISK tools must never be forcibly interrupted mid-execution (M8 invariant).</li>
 * </ol>
 */
class ToolExecutorTimeoutTest {

    record NoArgs() {}

    private ToolExecutor executor(Tool<?, ?> tool) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return new ToolExecutor(new ToolRegistry(List.of(tool)), new ObjectMapper(),
                validator, new SimpleMeterRegistry());
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(1L, "u@x.com", Set.of("ROLE_USER"));
    }

    /** A tool that sleeps for {@code sleepMs} before returning "done". */
    private Tool<NoArgs, String> slowTool(ToolRiskLevel risk, long sleepMs, long timeoutMs) {
        return new Tool<>() {
            @Override
            public ToolDescriptor descriptor() {
                return new ToolDescriptor("slow.tool", "slow", "test", "1", risk,
                        true, Set.of("ROLE_USER"), NoArgs.class, String.class,
                        Duration.ofMillis(timeoutMs));
            }

            @Override
            public String execute(ToolExecutionContext c, NoArgs in) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return "done";
            }
        };
    }

    // ─── Pre-execution deadline check ─────────────────────────────────────────

    @Test
    void deadline_already_past_returns_TOOL_TIMEOUT_before_execution() {
        // Agent deadline expired 10 seconds ago — tool must be rejected before any execution.
        ToolExecutionContext ctx = new ToolExecutionContext(
                user(), "req", "exec", Map.of(),
                Optional.of(Instant.now().minusSeconds(10)));
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.READ_ONLY, 1, 10_000))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void deadline_already_past_also_blocks_SIDE_EFFECTING_before_it_starts() {
        // Pre-execution check applies to all risk levels — only the in-flight interrupt is skipped
        // for SIDE_EFFECTING. If the deadline is already past, the tool should not start at all.
        ToolExecutionContext ctx = new ToolExecutionContext(
                user(), "req", "exec", Map.of(),
                Optional.of(Instant.now().minusSeconds(10)));
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.SIDE_EFFECTING, 1, 10_000))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void no_deadline_on_context_skips_deadline_check() {
        // ToolExecutionContext.forPrincipal() sets deadline = Optional.empty(); no check fires.
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user());
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.READ_ONLY, 10, 5_000))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isTrue();
    }

    // ─── In-flight Future timeout (READ_ONLY / DETERMINISTIC only) ────────────

    @Test
    void fast_READ_ONLY_tool_completes_within_descriptor_timeout() {
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user());
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.READ_ONLY, 10, 5_000))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEqualTo("done");
    }

    @Test
    void slow_READ_ONLY_tool_times_out_via_future() {
        // Tool sleeps 500ms but descriptor.timeout() is only 50ms → TOOL_TIMEOUT.
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user());
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.READ_ONLY, 500, 50))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void slow_DETERMINISTIC_tool_times_out_via_future() {
        // DETERMINISTIC is also eligible for in-flight Future timeout.
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user());
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.DETERMINISTIC, 500, 50))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void slow_SIDE_EFFECTING_tool_runs_to_completion_despite_short_descriptor_timeout() {
        // M8 invariant: SIDE_EFFECTING tools must NEVER be forcibly interrupted after starting.
        // Tool sleeps 200ms but descriptor.timeout() is only 50ms → tool still succeeds.
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user());
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.SIDE_EFFECTING, 200, 50))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEqualTo("done");
    }

    @Test
    void slow_HIGH_RISK_tool_runs_to_completion_despite_short_descriptor_timeout() {
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user());
        ToolResult<Object> r = executor(slowTool(ToolRiskLevel.HIGH_RISK, 200, 50))
                .execute("slow.tool", Map.of(), ctx);
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEqualTo("done");
    }
}
