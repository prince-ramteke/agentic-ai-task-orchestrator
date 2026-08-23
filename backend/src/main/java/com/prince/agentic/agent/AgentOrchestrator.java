package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.exception.AgentInvalidDecisionException;
import com.prince.agentic.ai.llm.exception.LlmException;
import com.prince.agentic.guardrail.GuardrailContext;
import com.prince.agentic.guardrail.GuardrailDecision;
import com.prince.agentic.guardrail.GuardrailEngine;
import com.prince.agentic.guardrail.GuardrailOutcome;
import com.prince.agentic.guardrail.RateLimiter;
import com.prince.agentic.guardrail.confirmation.PendingAction;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolExecutor;
import com.prince.agentic.tool.ToolResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The bounded agent loop (spec §8): decision -> validate -> guardrail -> execute -> observe -> repeat
 * until FINAL or a bound trips. Never touches a repository, {@code EntityManager}, or a domain service
 * directly; its only path to effects is {@link ToolExecutor}.
 *
 * <p><b>M9 audit:</b> at each lifecycle point the loop emits a backend-observed fact through the
 * repository-free {@link AgentAuditEmitter} → {@link AgentExecutionListener}. Emission is best-effort
 * (the recorder swallows failures) and never alters control flow. The orchestrator owns the per-run
 * step {@code sequence} and per-step timings.
 *
 * <p><b>Ruling R-B — external cancellation seam:</b> the public entry point delegates to a
 * package-private overload that accepts a {@link CancellationToken}.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final AgentPlanner planner;
    private final ToolExecutor toolExecutor;
    private final AgentToolCatalog catalog;
    private final ObservationSerializer observations;
    private final AgentProperties props;
    private final Clock clock;
    private final MeterRegistry meters;
    private final ObjectMapper mapper;
    private final GuardrailEngine guardrails;
    private final RateLimiter rateLimiter;
    private final AgentAuditEmitter audit;

    public AgentOrchestrator(AgentPlanner planner, ToolExecutor toolExecutor, AgentToolCatalog catalog,
                             ObservationSerializer observations, AgentProperties props, Clock clock,
                             MeterRegistry meters, ObjectMapper mapper,
                             GuardrailEngine guardrails, RateLimiter rateLimiter, AgentAuditEmitter audit) {
        this.planner = planner;
        this.toolExecutor = toolExecutor;
        this.catalog = catalog;
        this.observations = observations;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
        this.mapper = mapper;
        this.guardrails = guardrails;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    /** Public entry point: stateless (no conversation history), no external cancellation source. */
    public AgentResult run(AuthenticatedUser principal, String message) {
        return run(principal, message, "", null, () -> false);
    }

    /** Public entry point carrying bounded prior-conversation context (M7). */
    public AgentResult run(AuthenticatedUser principal, String message, String historyContext) {
        return run(principal, message, historyContext, null, () -> false);
    }

    /** Public entry point carrying bounded history + the conversation id (M7/M9 audit correlation). */
    public AgentResult run(AuthenticatedUser principal, String message, String historyContext,
                           String conversationId) {
        return run(principal, message, historyContext, conversationId, () -> false);
    }

    /** Package-private overload carrying only the external cancellation seam (spec ruling R-B). */
    AgentResult run(AuthenticatedUser principal, String message, CancellationToken external) {
        return run(principal, message, "", null, external);
    }

    /** Package-private core: bounded history + conversation id + external cancellation seam. */
    AgentResult run(AuthenticatedUser principal, String message, String historyContext,
                    String conversationId, CancellationToken external) {
        String executionId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        AgentExecution ex = new AgentExecution(executionId, principal, requestId, clock, props);
        LoopDetector loop = new LoopDetector(mapper, props.loopThreshold());
        audit.started(executionId, principal.userId(), conversationId, requestId, ex.startedAt());
        int seq = 0;

        // M10 (ADR-0030): push the executionId onto SLF4J MDC so every log line inside the loop
        // is correlated with the audit row. Save the prior value so nested runs on the same thread
        // (extremely rare, but possible via test wiring) restore what was there. Always cleared in
        // the outer finally — no id leaks across pool-reused threads.
        String priorExecutionMdc = MDC.get(MDC_EXECUTION_ID);
        MDC.put(MDC_EXECUTION_ID, executionId);
        try {
            while (true) {
                if (external.isCancelled()) {
                    return terminate(ex, AgentStatus.CANCELLED, "AGENT_CANCELLED", null);
                }
                if (ex.cancellation().isCancelled()) {
                    return terminate(ex, AgentStatus.TIMED_OUT, "AGENT_TIMEOUT", null);
                }
                if (ex.iteration() >= props.maxIterations()) {
                    limitMetric("iteration");
                    return terminate(ex, AgentStatus.LIMIT_REACHED, "AGENT_ITERATION_LIMIT", null);
                }
                ex.nextIteration();

                Instant dStart = clock.instant();
                AgentDecision decision;
                try {
                    decision = planner.decide(message, historyContext, ex.observations(),
                            props.maxIterations() - ex.iteration(), props.maxToolCalls() - ex.toolCallsUsed());
                } catch (AgentInvalidDecisionException e) {
                    audit.step(executionId, seq++, AgentStepKind.FAILURE, AgentStepOutcome.FAILED,
                            null, "AGENT_INVALID_DECISION", dStart, clock.instant());
                    return terminate(ex, AgentStatus.FAILED, "AGENT_INVALID_DECISION", null);
                } catch (LlmException e) {
                    audit.step(executionId, seq++, AgentStepKind.FAILURE, AgentStepOutcome.FAILED,
                            null, "AGENT_LLM_ERROR", dStart, clock.instant());
                    return terminate(ex, AgentStatus.FAILED, "AGENT_LLM_ERROR", null);
                }
                Instant dEnd = clock.instant();
                audit.step(executionId, seq++, AgentStepKind.LLM_DECISION, AgentStepOutcome.OK,
                        decision.tool(), decision.action().name(), dStart, dEnd);

                if (decision.action() == AgentAction.FINAL) {
                    audit.step(executionId, seq++, AgentStepKind.FINAL, AgentStepOutcome.OK,
                            null, null, dEnd, clock.instant());
                    return terminate(ex, AgentStatus.COMPLETED, null, decision.response());
                }

                if (ex.toolCallsUsed() >= props.maxToolCalls()) {
                    limitMetric("tool_call");
                    return terminate(ex, AgentStatus.LIMIT_REACHED, "AGENT_TOOL_CALL_LIMIT", null);
                }
                if (loop.isRepeat(decision.tool(), decision.arguments())) {
                    meters.counter("agent.loop.detected").increment();
                    return terminate(ex, AgentStatus.LOOP_DETECTED, "AGENT_LOOP_DETECTED", null);
                }

                // --- M8 guardrail gate (spec §11, §25): policy → confirmation → rate-limit → execute.
                GuardrailContext gctx = new GuardrailContext(ex.executionId(), ex.requestId());
                GuardrailDecision guard = guardrails.evaluate(principal, decision, gctx);
                if (guard.outcome() == GuardrailOutcome.DENY) {
                    int s = seq++;
                    audit.step(executionId, s, AgentStepKind.GUARDRAIL, AgentStepOutcome.BLOCKED,
                            decision.tool(), guard.reasonCode(), dEnd, clock.instant());
                    audit.toolExecution(executionId, s, decision.tool(), guard.riskLevel(),
                            decision.arguments(), AgentToolOutcome.DENIED, guard.reasonCode(), null, null,
                            dEnd, clock.instant());
                    return terminate(ex, AgentStatus.BLOCKED, guard.reasonCode(), null);
                }
                if (guard.requiresConfirmation()) {
                    int s = seq++;
                    audit.step(executionId, s, AgentStepKind.CONFIRMATION_REQUIRED, AgentStepOutcome.REQUIRED,
                            decision.tool(), guard.riskLevel().name(), dEnd, clock.instant());
                    audit.toolExecution(executionId, s, decision.tool(), guard.riskLevel(),
                            decision.arguments(), AgentToolOutcome.CONFIRMATION_REQUIRED, null, null, null,
                            dEnd, clock.instant());
                    PendingAction pending = new PendingAction(
                            executionId, decision.tool(), decision.arguments(), guard.riskLevel());
                    return terminatePending(ex, pending);
                }
                if (!rateLimiter.tryAcquire(principal.userId())) {
                    int s = seq++;
                    audit.step(executionId, s, AgentStepKind.GUARDRAIL, AgentStepOutcome.BLOCKED,
                            decision.tool(), "RATE_LIMITED", dEnd, clock.instant());
                    audit.toolExecution(executionId, s, decision.tool(), guard.riskLevel(),
                            decision.arguments(), AgentToolOutcome.NOT_EXECUTED, "RATE_LIMITED", null, null,
                            dEnd, clock.instant());
                    return terminate(ex, AgentStatus.BLOCKED, "RATE_LIMITED", null);
                }

                Instant tStart = clock.instant();
                ToolExecutionContext ctx = new ToolExecutionContext(
                        principal, ex.requestId(), ex.executionId(), Map.of(),
                        Optional.of(ex.deadline())); // H-03: propagate agent deadline to tool enforcement
                ToolResult<Object> result = toolExecutor.execute(
                        decision.tool(), decision.arguments(), ctx);
                Instant tEnd = clock.instant();
                ex.recordToolCall();
                meters.counter("agent.tool.calls").increment();
                AgentObservation obs = observations.toObservation(result);
                ex.addObservation(obs);

                int s = seq++;
                boolean ok = result.success();
                audit.step(executionId, s, AgentStepKind.TOOL_CALL,
                        ok ? AgentStepOutcome.OK : AgentStepOutcome.FAILED,
                        decision.tool(), ok ? null : obs.errorCode(), tStart, tEnd);
                // Emit a tool-execution row only when a real tool ran (skip an unresolved TOOL_NOT_FOUND,
                // whose risk is unknown and which never executed).
                if (!"TOOL_NOT_FOUND".equals(obs.errorCode())) {
                    audit.toolExecution(executionId, s, decision.tool(), guard.riskLevel(),
                            decision.arguments(), ok ? AgentToolOutcome.SUCCESS : AgentToolOutcome.FAILED,
                            ok ? null : obs.errorCode(), null, obs.resultSummary(), tStart, tEnd);
                }
            }
        } catch (RuntimeException unexpected) {
            log.warn("agent.run unexpected failure executionId={}", executionId, unexpected);
            return terminate(ex, AgentStatus.FAILED, "AGENT_EXECUTION_FAILED", null);
        } finally {
            if (priorExecutionMdc == null) {
                MDC.remove(MDC_EXECUTION_ID);
            } else {
                MDC.put(MDC_EXECUTION_ID, priorExecutionMdc);
            }
        }
    }

    /** SLF4J MDC key correlating log lines to an agent execution. See ADR-0030. */
    static final String MDC_EXECUTION_ID = "executionId";

    private AgentResult terminate(AgentExecution ex, AgentStatus status, String failureCode, String response) {
        long ms = ex.elapsedMillis(clock);
        meters.timer("agent.execution.duration", "status", status.name()).record(Duration.ofMillis(ms));
        meters.counter("agent.execution.count", "status", status.name()).increment();
        meters.summary("agent.iterations").record(ex.iteration());
        log.info("agent.run executionId={} status={} iterations={} toolCalls={} durationMs={}",
                ex.executionId(), status, ex.iteration(), ex.toolCallsUsed(), ms);
        audit.completed(ex.executionId(), status, failureCode, response,
                ex.iteration(), ex.toolCallsUsed(), clock.instant(), ms);
        return new AgentResult(ex.executionId(), status, response,
                ex.iteration(), ex.toolCallsUsed(), ms, failureCode, ex.observations());
    }

    /**
     * Terminate a run because a guardrail requires confirmation (spec §11). The execution is recorded
     * as terminal {@code PENDING_CONFIRMATION}; a later successful confirm promotes it (M9). No tool
     * has executed.
     */
    private AgentResult terminatePending(AgentExecution ex, PendingAction pending) {
        long ms = ex.elapsedMillis(clock);
        AgentStatus status = AgentStatus.PENDING_CONFIRMATION;
        meters.timer("agent.execution.duration", "status", status.name()).record(Duration.ofMillis(ms));
        meters.counter("agent.execution.count", "status", status.name()).increment();
        meters.summary("agent.iterations").record(ex.iteration());
        log.info("agent.run executionId={} status={} iterations={} toolCalls={} durationMs={} pendingTool={}",
                ex.executionId(), status, ex.iteration(), ex.toolCallsUsed(), ms, pending.tool());
        audit.completed(ex.executionId(), status, "CONFIRMATION_REQUIRED", null,
                ex.iteration(), ex.toolCallsUsed(), clock.instant(), ms);
        return new AgentResult(ex.executionId(), status, null,
                ex.iteration(), ex.toolCallsUsed(), ms, "CONFIRMATION_REQUIRED", ex.observations(), pending);
    }

    private void limitMetric(String limit) {
        meters.counter("agent.limit.reached", "limit", limit).increment();
    }
}
