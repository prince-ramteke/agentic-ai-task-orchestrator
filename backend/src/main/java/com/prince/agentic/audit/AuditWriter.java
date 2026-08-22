package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentStepKind;
import com.prince.agentic.agent.AgentStepOutcome;
import com.prince.agentic.agent.AgentToolOutcome;
import com.prince.agentic.agent.AuditConfirmationExecuted;
import com.prince.agentic.agent.AuditExecutionEnd;
import com.prince.agentic.agent.AuditExecutionStart;
import com.prince.agentic.agent.AuditStepEvent;
import com.prince.agentic.agent.AuditToolEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The transactional persistence worker for audit (spec §9). Every method runs in its <b>own</b> short
 * transaction ({@link Propagation#REQUIRES_NEW}) that opens and commits around a single write only — it
 * never spans an LLM or tool/domain transaction. Writes are <b>idempotent by check-before-insert</b>
 * against the UNIQUE natural keys, so a re-emitted event does not duplicate a row. Methods may throw;
 * the best-effort swallow + metrics live in {@link AuditService}, which calls this worker so the
 * {@code REQUIRES_NEW} boundary is a real proxy boundary (not a self-invocation).
 */
@Component
class AuditWriter {

    private final AgentExecutionRepository executions;
    private final AgentStepRepository steps;
    private final ToolExecutionRepository toolExecutions;
    private final AuditProperties props;

    AuditWriter(AgentExecutionRepository executions, AgentStepRepository steps,
                ToolExecutionRepository toolExecutions, AuditProperties props) {
        this.executions = executions;
        this.steps = steps;
        this.toolExecutions = toolExecutions;
        this.props = props;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void createExecution(AuditExecutionStart e) {
        if (executions.findByExecutionUid(e.executionUid()).isPresent()) {
            return; // idempotent: already recorded
        }
        executions.save(new AgentExecutionRecord(e.executionUid(), e.ownerId(), e.conversationId(),
                e.requestId(), AuditExecutionStatus.STARTED, e.startedAt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void addStep(AuditStepEvent e) {
        Optional<AgentExecutionRecord> exec = executions.findByExecutionUid(e.executionUid());
        if (exec.isEmpty()) {
            return; // start write was lost (best-effort); nothing to attach to
        }
        Long executionId = exec.get().getId();
        if (steps.existsByExecutionIdAndSequence(executionId, e.sequence())) {
            return; // idempotent on (execution_id, sequence)
        }
        steps.save(new AgentStepRecord(executionId, e.sequence(), e.kind(), e.outcome(),
                e.toolName(), e.detailCode(), e.startedAt(), e.completedAt(), e.durationMs()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void addToolExecution(AuditToolEvent e) {
        if (toolExecutions.existsByToolExecutionUid(e.toolExecutionUid())) {
            return; // idempotent on tool_execution_uid
        }
        Optional<AgentExecutionRecord> exec = executions.findByExecutionUid(e.executionUid());
        if (exec.isEmpty()) {
            return;
        }
        AgentExecutionRecord execution = exec.get();
        Long stepId = steps.findByExecutionIdOrderBySequenceAsc(execution.getId()).stream()
                .filter(s -> s.getSequence() == e.stepSequence())
                .map(AgentStepRecord::getId)
                .findFirst()
                .orElse(null);
        if (stepId == null) {
            return; // the owning TOOL_CALL step write was lost; skip rather than orphan
        }
        toolExecutions.save(new ToolExecutionRecord(e.toolExecutionUid(), stepId, execution.getId(),
                execution.getOwnerId(), e.toolName(), e.riskLevel(), e.outcome(), e.errorCode(),
                e.confirmationId(), e.argumentsHash(), cap(e.resultSummary(), props.resultSummaryMaxChars()),
                e.startedAt(), e.completedAt(), e.durationMs()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void completeExecution(AuditExecutionEnd e) {
        Optional<AgentExecutionRecord> exec = executions.findByExecutionUid(e.executionUid());
        if (exec.isEmpty()) {
            return;
        }
        AuditExecutionStatus status = AuditExecutionStatus.valueOf(e.status().name());
        exec.get().complete(status, e.failureCode(),
                cap(e.finalResponseSummary(), props.finalSummaryMaxChars()),
                e.iterations(), e.toolCalls(), e.completedAt(), e.durationMs());
        // managed entity → flushed on commit; no explicit save needed.
    }

    /**
     * Confirm path (spec §8): append a {@code CONFIRMATION_APPROVED} step + its tool execution to the
     * originating run (next sequence assigned here), and promote the run's {@code PENDING_CONFIRMATION}
     * to {@code COMPLETED}/{@code FAILED}. Idempotent per confirmation (a confirmation is single-use).
     * The run's iterations/duration are preserved; toolCalls is incremented by the one confirmed call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordConfirmationExecution(AuditConfirmationExecuted e) {
        Optional<AgentExecutionRecord> found = executions.findByExecutionUid(e.executionUid());
        if (found.isEmpty()) {
            return;
        }
        AgentExecutionRecord execution = found.get();
        if (toolExecutions.existsByExecutionIdAndConfirmationId(execution.getId(), e.confirmationId())) {
            return; // already recorded for this single-use confirmation
        }

        int sequence = steps.maxSequence(execution.getId()) + 1;
        AgentStepOutcome stepOutcome = e.success() ? AgentStepOutcome.OK : AgentStepOutcome.FAILED;
        AgentStepRecord step = steps.save(new AgentStepRecord(execution.getId(), sequence,
                AgentStepKind.CONFIRMATION_APPROVED, stepOutcome, e.toolName(), e.errorCode(),
                e.startedAt(), e.completedAt(), millis(e.startedAt(), e.completedAt())));

        AgentToolOutcome toolOutcome = e.success() ? AgentToolOutcome.SUCCESS : AgentToolOutcome.FAILED;
        toolExecutions.save(new ToolExecutionRecord(UUID.randomUUID().toString(), step.getId(),
                execution.getId(), execution.getOwnerId(), e.toolName(), e.riskLevel(), toolOutcome,
                e.errorCode(), e.confirmationId(), e.argumentsHash(),
                cap(e.resultSummary(), props.resultSummaryMaxChars()),
                e.startedAt(), e.completedAt(), millis(e.startedAt(), e.completedAt())));

        // Promote only from PENDING_CONFIRMATION; preserve iterations and the run's own duration.
        if (execution.getStatus() == AuditExecutionStatus.PENDING_CONFIRMATION) {
            AuditExecutionStatus promoted = e.success()
                    ? AuditExecutionStatus.COMPLETED : AuditExecutionStatus.FAILED;
            execution.complete(promoted, e.success() ? null : e.errorCode(),
                    execution.getFinalResponseSummary(), execution.getIterations(),
                    execution.getToolCalls() + 1, e.completedAt(), execution.getDurationMs());
        }
    }

    private static Long millis(java.time.Instant a, java.time.Instant b) {
        return (a == null || b == null) ? null : java.time.Duration.between(a, b).toMillis();
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
