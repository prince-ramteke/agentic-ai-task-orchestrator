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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * The bounded agent loop (spec §8): decision -> validate -> execute -> observe -> repeat until
 * FINAL or a bound trips. Never touches a repository, {@code EntityManager}, or a domain service
 * directly; its only path to effects is {@link ToolExecutor}.
 *
 * <p><b>Ruling R-B — external cancellation seam:</b> the public entry point delegates to a
 * package-private overload that accepts a {@link CancellationToken}, so an M8 caller (or a test)
 * can inject external cancellation without touching the deadline-backed token owned by the
 * {@link AgentExecution}. At the top of every iteration, three independent checks run in order,
 * each its own branch: external cancellation, deadline (via {@code AgentExecution.cancellation()}),
 * then the iteration budget.
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

    public AgentOrchestrator(AgentPlanner planner, ToolExecutor toolExecutor, AgentToolCatalog catalog,
                             ObservationSerializer observations, AgentProperties props, Clock clock,
                             MeterRegistry meters, ObjectMapper mapper,
                             GuardrailEngine guardrails, RateLimiter rateLimiter) {
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
    }

    /** Public entry point: stateless (no conversation history), no external cancellation source. */
    public AgentResult run(AuthenticatedUser principal, String message) {
        return run(principal, message, "");
    }

    /**
     * Public entry point carrying bounded prior-conversation context (M7). {@code historyContext} is
     * an already-rendered, already-bounded string supplied by the memory layer; the orchestrator
     * treats it as opaque untrusted text and never touches Redis itself.
     */
    public AgentResult run(AuthenticatedUser principal, String message, String historyContext) {
        return run(principal, message, historyContext, () -> false);
    }

    /** Package-private overload carrying only the external cancellation seam (spec ruling R-B). */
    AgentResult run(AuthenticatedUser principal, String message, CancellationToken external) {
        return run(principal, message, "", external);
    }

    /** Package-private core: bounded history + external cancellation seam. */
    AgentResult run(AuthenticatedUser principal, String message, String historyContext,
                    CancellationToken external) {
        String executionId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        AgentExecution ex = new AgentExecution(executionId, principal, requestId, clock, props);
        LoopDetector loop = new LoopDetector(mapper, props.loopThreshold());

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

                AgentDecision decision;
                try {
                    decision = planner.decide(message, historyContext, ex.observations(),
                            props.maxIterations() - ex.iteration(), props.maxToolCalls() - ex.toolCallsUsed());
                } catch (AgentInvalidDecisionException e) {
                    return terminate(ex, AgentStatus.FAILED, "AGENT_INVALID_DECISION", null);
                } catch (LlmException e) {
                    return terminate(ex, AgentStatus.FAILED, "AGENT_LLM_ERROR", null);
                }

                if (decision.action() == AgentAction.FINAL) {
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
                // No effect can occur before this passes. The engine is authoritative over risk.
                GuardrailContext gctx = new GuardrailContext(ex.executionId(), ex.requestId());
                GuardrailDecision guard = guardrails.evaluate(principal, decision, gctx);
                if (guard.outcome() == GuardrailOutcome.DENY) {
                    return terminate(ex, AgentStatus.BLOCKED, guard.reasonCode(), null);
                }
                if (guard.requiresConfirmation()) {
                    PendingAction pending =
                            new PendingAction(decision.tool(), decision.arguments(), guard.riskLevel());
                    return terminatePending(ex, pending);
                }
                if (!rateLimiter.tryAcquire(principal.userId())) {
                    return terminate(ex, AgentStatus.BLOCKED, "RATE_LIMITED", null);
                }

                ToolExecutionContext ctx = new ToolExecutionContext(
                        principal, ex.requestId(), ex.executionId(), Map.of());
                ToolResult<Object> result = toolExecutor.execute(
                        decision.tool(), decision.arguments(), ctx);
                ex.recordToolCall();
                meters.counter("agent.tool.calls").increment();
                ex.addObservation(observations.toObservation(result));
            }
        } catch (RuntimeException unexpected) {
            log.warn("agent.run unexpected failure executionId={}", executionId, unexpected);
            return terminate(ex, AgentStatus.FAILED, "AGENT_EXECUTION_FAILED", null);
        }
    }

    private AgentResult terminate(AgentExecution ex, AgentStatus status, String failureCode, String response) {
        long ms = ex.elapsedMillis(clock);
        meters.timer("agent.execution.duration", "status", status.name()).record(Duration.ofMillis(ms));
        meters.counter("agent.execution.count", "status", status.name()).increment();
        meters.summary("agent.iterations").record(ex.iteration());
        log.info("agent.run executionId={} status={} iterations={} toolCalls={} durationMs={}",
                ex.executionId(), status, ex.iteration(), ex.toolCallsUsed(), ms);
        return new AgentResult(ex.executionId(), status, response,
                ex.iteration(), ex.toolCallsUsed(), ms, failureCode, ex.observations());
    }

    /**
     * Terminate a run because a guardrail requires confirmation (spec §11). Records the same lifecycle
     * metrics as {@link #terminate} and carries the exact {@link PendingAction} out; the conversation
     * layer binds it to a stored, fingerprint-bound confirmation. No tool has executed.
     */
    private AgentResult terminatePending(AgentExecution ex, PendingAction pending) {
        long ms = ex.elapsedMillis(clock);
        AgentStatus status = AgentStatus.PENDING_CONFIRMATION;
        meters.timer("agent.execution.duration", "status", status.name()).record(Duration.ofMillis(ms));
        meters.counter("agent.execution.count", "status", status.name()).increment();
        meters.summary("agent.iterations").record(ex.iteration());
        log.info("agent.run executionId={} status={} iterations={} toolCalls={} durationMs={} pendingTool={}",
                ex.executionId(), status, ex.iteration(), ex.toolCallsUsed(), ms, pending.tool());
        return new AgentResult(ex.executionId(), status, null,
                ex.iteration(), ex.toolCallsUsed(), ms, "CONFIRMATION_REQUIRED", ex.observations(), pending);
    }

    private void limitMetric(String limit) {
        meters.counter("agent.limit.reached", "limit", limit).increment();
    }
}
