package com.prince.agentic.agent;

import com.prince.agentic.guardrail.RateLimiter;
import com.prince.agentic.guardrail.confirmation.ConfirmationService;
import com.prince.agentic.guardrail.confirmation.ConfirmedAction;
import com.prince.agentic.guardrail.exception.RateLimitedException;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolExecutor;
import com.prince.agentic.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Executes a confirmed side-effecting action exactly once (spec §6.4). Order (spec §25):
 * rate-limit → single-use consume → execute through the <em>same</em> {@link ToolExecutor} gates
 * (auth, ownership, validation). The confirmation authorizes intent; it never bypasses authorization,
 * and the executed arguments are the stored ones — never a client-supplied payload.
 *
 * <p>Rate-limiting runs before the consume so a throttled confirm leaves the pending action intact to
 * retry in the next window; single-use is guaranteed by {@link ConfirmationService#confirm}, so the
 * action can execute at most once even under concurrent or replayed confirms.
 */
@Service
public class AgentConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(AgentConfirmationService.class);

    private final ConfirmationService confirmations;
    private final RateLimiter rateLimiter;
    private final ToolExecutor toolExecutor;
    private final ObservationSerializer observations;
    private final AgentAuditEmitter audit;
    private final Clock clock;

    public AgentConfirmationService(ConfirmationService confirmations, RateLimiter rateLimiter,
                                    ToolExecutor toolExecutor, ObservationSerializer observations,
                                    AgentAuditEmitter audit, Clock clock) {
        this.confirmations = confirmations;
        this.rateLimiter = rateLimiter;
        this.toolExecutor = toolExecutor;
        this.observations = observations;
        this.audit = audit;
        this.clock = clock;
    }

    public AgentConfirmationOutcome confirm(AuthenticatedUser principal, String confirmationId) {
        if (!rateLimiter.tryAcquire(principal.userId())) {
            throw new RateLimitedException();
        }
        // Single-use consume; throws (NOT_FOUND/EXPIRED/MISMATCH/ALREADY_USED) on anything invalid.
        ConfirmedAction action = confirmations.confirm(principal, confirmationId);

        Instant start = clock.instant();
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(principal);
        ToolResult<Object> result = toolExecutor.execute(action.toolName(), action.arguments(), ctx);
        Instant end = clock.instant();
        AgentObservation obs = observations.toObservation(result);

        // Audit the confirmed execution against the originating run (best-effort; never blocks).
        audit.confirmationExecuted(action.executionId(), confirmationId, action.toolName(),
                action.riskLevel(), action.arguments(), obs.success(), obs.errorCode(),
                obs.resultSummary(), start, end);

        log.info("agent.confirmation.executed id={} tool={} success={} user={}",
                confirmationId, action.toolName(), obs.success(), principal.userId());
        return new AgentConfirmationOutcome(
                confirmationId, action.toolName(), obs.success(), obs.resultSummary(), obs.errorCode());
    }

    public void cancel(AuthenticatedUser principal, String confirmationId) {
        confirmations.cancel(principal, confirmationId);
    }
}
