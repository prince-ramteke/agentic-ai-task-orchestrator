package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.support.ScriptedLlmClient;
import com.prince.agentic.guardrail.GuardrailDecision;
import com.prince.agentic.guardrail.GuardrailEngine;
import com.prince.agentic.guardrail.RateLimiter;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolError;
import com.prince.agentic.tool.ToolExecutor;
import com.prince.agentic.tool.ToolResult;
import com.prince.agentic.tool.ToolRiskLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Covers the bounded loop end to end: every terminal status, both cancellation/timeout seams
 * (spec ruling R-B), the planner's one-repair path, and observation recovery for each tool
 * failure class. Wires a real {@link AgentPlanner} to a scripted {@link ScriptedLlmClient} so
 * decision validation/repair is exercised for real; only {@link ToolExecutor} and
 * {@link AgentToolCatalog} are mocked.
 */
class AgentOrchestratorTest {

    private final AuthenticatedUser user = new AuthenticatedUser(1L, "a@b.com", Set.of("ROLE_USER"));
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC);
    private final AgentProperties props = new AgentProperties(8, 10, 60, 2, 2000, 20);

    /**
     * A guardrail engine mimicking the real risk policy for these mechanics tests: {@code task.create}
     * is SIDE_EFFECTING → REQUIRE_CONFIRMATION; every other tool ALLOWs so the executor path (and its
     * observation recovery) is still exercised. The real engine is unit-tested separately.
     */
    private GuardrailEngine riskEngine() {
        return (principal, decision, ctx) -> "task.create".equals(decision.tool())
                ? GuardrailDecision.requireConfirmation(ToolRiskLevel.SIDE_EFFECTING, "risk")
                : GuardrailDecision.allow();
    }

    /** Rate limiter that always admits (rate-limit behaviour has its own test). */
    private RateLimiter alwaysAllow() {
        return userId -> true;
    }

    private AgentOrchestrator orchestrator(ScriptedLlmClient llm, ToolExecutor executor, AgentToolCatalog catalog) {
        return orchestrator(llm, executor, catalog, props, clock);
    }

    private AgentOrchestrator orchestrator(ScriptedLlmClient llm, ToolExecutor executor, AgentToolCatalog catalog,
                                           AgentProperties p, Clock c) {
        return orchestrator(llm, executor, catalog, p, c, riskEngine(), alwaysAllow());
    }

    private AgentOrchestrator orchestrator(ScriptedLlmClient llm, ToolExecutor executor, AgentToolCatalog catalog,
                                           AgentProperties p, Clock c, GuardrailEngine engine, RateLimiter limiter) {
        return orchestrator(llm, executor, catalog, p, c, engine, limiter, new NoOpAgentExecutionListener());
    }

    private AgentOrchestrator orchestrator(ScriptedLlmClient llm, ToolExecutor executor, AgentToolCatalog catalog,
                                           AgentProperties p, Clock c, GuardrailEngine engine, RateLimiter limiter,
                                           AgentExecutionListener listener) {
        ObjectMapper om = new ObjectMapper();
        AgentPlanner planner = new AgentPlanner(llm, new AgentPromptService(), new AgentDecisionValidator(), catalog);
        AgentAuditEmitter audit = new AgentAuditEmitter(listener,
                new com.prince.agentic.guardrail.FingerprintService(om));
        return new AgentOrchestrator(planner, executor, catalog,
                new ObservationSerializer(om, p), p, c, new SimpleMeterRegistry(), om, engine, limiter, audit);
    }

    private AgentToolCatalog emptyCatalog() {
        AgentToolCatalog catalog = mock(AgentToolCatalog.class);
        when(catalog.render()).thenReturn("");
        return catalog;
    }

    @Test
    void directFinal_completesWithoutToolCall() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .enqueueStructured(new AgentDecision(AgentAction.FINAL, "Hello!", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "hi");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isZero();
        assertThat(r.finalResponse()).isEqualTo("Hello!");
        assertThat(r.failureCode()).isNull();
        verifyNoInteractions(executor);
    }

    @Test
    void singleToolCall_thenFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("task.search"), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.search", List.of("t1", "t2", "t3"), 5));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "HIGH")),
                new AgentDecision(AgentAction.FINAL, "You have 3.", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "high tasks?");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isEqualTo(1);
        assertThat(r.finalResponse()).isEqualTo("You have 3.");
    }

    @Test
    void multipleReadOnlyToolCalls_thenFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("task.search"), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.search", List.of("t1"), 3));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "HIGH")),
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "LOW")),
                new AgentDecision(AgentAction.FINAL, "done", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "do stuff");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isEqualTo(2);
        verify(executor, times(2)).execute(eq("task.search"), anyMap(), any());
    }

    @Test
    void malformedDecision_repairsToValidFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.FINAL, null, null, null), // invalid: blank response
                new AgentDecision(AgentAction.FINAL, "repaired answer", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "hi");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.finalResponse()).isEqualTo("repaired answer");
        verifyNoInteractions(executor);
    }

    @Test
    void invalidDecisionAfterRepair_returnsFailedInvalidDecision() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.FINAL, null, null, null),  // invalid
                new AgentDecision(AgentAction.FINAL, null, null, null)); // still invalid after repair
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "hi");
        assertThat(r.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(r.failureCode()).isEqualTo("AGENT_INVALID_DECISION");
        verifyNoInteractions(executor);
    }

    @Test
    void toolNotFoundObservation_thenRecoversToFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("bogus.tool"), anyMap(), any()))
                .thenReturn(ToolResult.failure("bogus.tool",
                        new ToolError("TOOL_NOT_FOUND", "unknown tool: bogus.tool"), 1));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "bogus.tool", Map.of()),
                new AgentDecision(AgentAction.FINAL, "recovered", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isEqualTo(1);
        assertThat(r.finalResponse()).isEqualTo("recovered");
    }

    @Test
    void toolUnauthorizedObservation_thenRecoversToFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("task.delete"), anyMap(), any()))
                .thenReturn(ToolResult.failure("task.delete",
                        new ToolError("TOOL_UNAUTHORIZED", "authentication required"), 1));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.delete", Map.of("id", 1)),
                new AgentDecision(AgentAction.FINAL, "cannot do that", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "delete it");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isEqualTo(1);
    }

    @Test
    void toolForbiddenObservation_thenRecoversToFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("task.admin"), anyMap(), any()))
                .thenReturn(ToolResult.failure("task.admin",
                        new ToolError("TOOL_FORBIDDEN", "missing required role"), 1));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.admin", Map.of()),
                new AgentDecision(AgentAction.FINAL, "not permitted", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isEqualTo(1);
    }

    @Test
    void toolFailureObservation_thenRecoversToFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("task.search"), anyMap(), any()))
                .thenReturn(ToolResult.failure("task.search",
                        new ToolError("TOOL_EXECUTION_FAILED", "tool execution failed"), 1));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of()),
                new AgentDecision(AgentAction.FINAL, "search failed", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(r.toolCalls()).isEqualTo(1);
    }

    @Test
    void maxIterationsReached_returnsLimitReached() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(any(), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.search", List.of(), 1));
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient();
        // Distinct arguments each time so loop detection (threshold=2) never trips.
        for (int i = 0; i < 10; i++) {
            llm.enqueueStructured(new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("p", "P" + i)));
        }
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.LIMIT_REACHED);
        assertThat(r.failureCode()).isEqualTo("AGENT_ITERATION_LIMIT");
        assertThat(r.iterations()).isEqualTo(8);
    }

    @Test
    void maxToolCallsReached_returnsLimitReached() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(any(), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.search", List.of(), 1));
        AgentToolCatalog catalog = emptyCatalog();
        // High iteration budget, low tool-call budget so the tool-call bound trips first.
        AgentProperties smallToolCallProps = new AgentProperties(20, 3, 60, 2, 2000, 20);
        ScriptedLlmClient llm = new ScriptedLlmClient();
        for (int i = 0; i < 6; i++) {
            llm.enqueueStructured(new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("p", "P" + i)));
        }
        AgentResult r = orchestrator(llm, executor, catalog, smallToolCallProps, clock).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.LIMIT_REACHED);
        assertThat(r.failureCode()).isEqualTo("AGENT_TOOL_CALL_LIMIT");
        assertThat(r.toolCalls()).isEqualTo(3);
    }

    @Test
    void loopDetected_whenSameToolCallRepeats() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(any(), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.search", List.of(), 1));
        AgentToolCatalog catalog = emptyCatalog();
        AgentDecision same = new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("p", "HIGH"));
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(same, same, same, same);
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.LOOP_DETECTED);
        assertThat(r.failureCode()).isEqualTo("AGENT_LOOP_DETECTED");
    }

    @Test
    void deadlineExceeded_returnsTimedOut() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        Instant start = Instant.parse("2026-08-21T00:00:00Z");
        Instant later = start.plusSeconds(1000); // well past the 60s deadline
        Clock advancing = new OneShotThenAdvancingClock(start, later);
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .enqueueStructured(new AgentDecision(AgentAction.FINAL, "should not be reached", null, null));
        AgentResult r = orchestrator(llm, executor, catalog, props, advancing).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.TIMED_OUT);
        assertThat(r.failureCode()).isEqualTo("AGENT_TIMEOUT");
        verifyNoInteractions(executor);
        assertThat(llm.prompts()).isEmpty(); // trips before the planner is ever consulted
    }

    @Test
    void externalCancellation_returnsCancelled() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient()
                .enqueueStructured(new AgentDecision(AgentAction.FINAL, "unused", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x", () -> true);
        assertThat(r.status()).isEqualTo(AgentStatus.CANCELLED);
        assertThat(r.failureCode()).isEqualTo("AGENT_CANCELLED");
        verifyNoInteractions(executor);
        assertThat(llm.prompts()).isEmpty();
    }

    @Test
    void providerError_becomesFailedResult_notThrow() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().setMode(ScriptedLlmClient.Mode.UNAVAILABLE);
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(r.failureCode()).isEqualTo("AGENT_LLM_ERROR");
    }

    @Test
    void sideEffectTool_haltsAtPendingConfirmation_withoutExecuting() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.create", Map.of("title", "x")),
                new AgentDecision(AgentAction.FINAL, "should not be reached", null, null));
        AgentResult r = orchestrator(llm, executor, catalog).run(user, "create a task");
        assertThat(r.status()).isEqualTo(AgentStatus.PENDING_CONFIRMATION);
        assertThat(r.failureCode()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(r.toolCalls()).isZero();
        assertThat(r.pending()).isNotNull();
        assertThat(r.pending().tool()).isEqualTo("task.create");
        assertThat(r.pending().riskLevel()).isEqualTo(ToolRiskLevel.SIDE_EFFECTING);
        assertThat(r.pending().arguments()).containsEntry("title", "x");
        verifyNoInteractions(executor); // guardrail halts BEFORE any effect
    }

    @Test
    void guardrailDeny_returnsBlocked_withoutExecuting() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        GuardrailEngine deny = (p, d, ctx) -> GuardrailDecision.deny("UNSAFE_ACTION", "no", "arg-safety");
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("p", "x")));
        AgentResult r = orchestrator(llm, executor, catalog, props, clock, deny, alwaysAllow())
                .run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.BLOCKED);
        assertThat(r.failureCode()).isEqualTo("UNSAFE_ACTION");
        verifyNoInteractions(executor);
    }

    @Test
    void rateLimitExceeded_returnsBlocked_withoutExecuting() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        RateLimiter denyAll = userId -> false;
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("p", "x")));
        AgentResult r = orchestrator(llm, executor, catalog, props, clock, riskEngine(), denyAll)
                .run(user, "x");
        assertThat(r.status()).isEqualTo(AgentStatus.BLOCKED);
        assertThat(r.failureCode()).isEqualTo("RATE_LIMITED");
        verifyNoInteractions(executor);
    }

    @Test
    void auditEvents_areEmitted_forSearchThenFinal() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("task.search"), anyMap(), any()))
                .thenReturn(ToolResult.ok("task.search", List.of("t1"), 3));
        AgentToolCatalog catalog = emptyCatalog();
        CapturingListener listener = new CapturingListener();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("p", "HIGH")),
                new AgentDecision(AgentAction.FINAL, "done", null, null));

        AgentResult r = orchestrator(llm, executor, catalog, props, clock, riskEngine(), alwaysAllow(), listener)
                .run(user, "x");

        assertThat(r.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(listener.started).isEqualTo(1);
        assertThat(listener.completed).isEqualTo(1);
        assertThat(listener.lastStatus).isEqualTo(AgentStatus.COMPLETED);
        // At least: LLM_DECISION, TOOL_CALL, LLM_DECISION, FINAL steps + one tool execution.
        assertThat(listener.stepKinds).contains(AgentStepKind.LLM_DECISION, AgentStepKind.TOOL_CALL,
                AgentStepKind.FINAL);
        assertThat(listener.toolExecutions).isEqualTo(1);
    }

    @Test
    void auditCompleted_reflectsPendingConfirmation_forSideEffectTool() {
        ToolExecutor executor = mock(ToolExecutor.class);
        AgentToolCatalog catalog = emptyCatalog();
        CapturingListener listener = new CapturingListener();
        ScriptedLlmClient llm = new ScriptedLlmClient().enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.create", Map.of("title", "x")));

        AgentResult r = orchestrator(llm, executor, catalog, props, clock, riskEngine(), alwaysAllow(), listener)
                .run(user, "x");

        assertThat(r.status()).isEqualTo(AgentStatus.PENDING_CONFIRMATION);
        assertThat(listener.lastStatus).isEqualTo(AgentStatus.PENDING_CONFIRMATION);
        assertThat(listener.stepKinds).contains(AgentStepKind.CONFIRMATION_REQUIRED);
    }

    /** Captures emitted audit facts so a test can assert lifecycle emission without a database. */
    private static final class CapturingListener implements AgentExecutionListener {
        int started;
        int completed;
        int toolExecutions;
        AgentStatus lastStatus;
        final java.util.List<AgentStepKind> stepKinds = new java.util.ArrayList<>();

        @Override public void onExecutionStarted(AuditExecutionStart e) { started++; }
        @Override public void onStep(AuditStepEvent e) { stepKinds.add(e.kind()); }
        @Override public void onToolExecution(AuditToolEvent e) { toolExecutions++; }
        @Override public void onExecutionCompleted(AuditExecutionEnd e) { completed++; lastStatus = e.status(); }
        @Override public void onConfirmationExecuted(AuditConfirmationExecuted e) { }
    }

    /** Returns {@code start} on its first call (used by AgentExecution to compute the deadline),
     *  then {@code later} on every subsequent call, so the deadline trips before any tool runs. */
    private static final class OneShotThenAdvancingClock extends Clock {
        private final Instant start;
        private final Instant later;
        private int calls = 0;

        OneShotThenAdvancingClock(Instant start, Instant later) {
            this.start = start;
            this.later = later;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ++calls == 1 ? start : later; }
    }
}
