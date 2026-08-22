package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentStepKind;
import com.prince.agentic.agent.AgentStepOutcome;
import com.prince.agentic.agent.AgentStatus;
import com.prince.agentic.agent.AgentToolOutcome;
import com.prince.agentic.agent.AuditExecutionEnd;
import com.prince.agentic.agent.AuditExecutionStart;
import com.prince.agentic.agent.AuditStepEvent;
import com.prince.agentic.agent.AuditToolEvent;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Idempotency (check-before-insert), bounding/redaction, status mapping, and skip-on-missing. */
class AuditWriterTest {

    private final AgentExecutionRepository executions = mock(AgentExecutionRepository.class);
    private final AgentStepRepository steps = mock(AgentStepRepository.class);
    private final ToolExecutionRepository toolExecutions = mock(ToolExecutionRepository.class);
    private final AuditProperties props = new AuditProperties(90, 10, 10); // tiny caps to prove bounding
    private final AuditWriter writer = new AuditWriter(executions, steps, toolExecutions, props);

    private static final Instant T = Instant.parse("2026-08-22T12:00:00Z");

    private AgentExecutionRecord execRecord(long id, long owner) {
        AgentExecutionRecord r = new AgentExecutionRecord("exec-uid", owner, "conv", "req",
                AuditExecutionStatus.STARTED, T);
        // set the generated id via reflection-free helper: re-create is not possible, so use a spy-like stub
        return new AgentExecutionRecordWithId(id, r);
    }

    @Test
    void createExecution_insertsWhenAbsent_skipsWhenPresent() {
        when(executions.findByExecutionUid("exec-uid")).thenReturn(Optional.empty());
        writer.createExecution(new AuditExecutionStart("exec-uid", 1L, "conv", "req", T));
        verify(executions).save(any(AgentExecutionRecord.class));

        when(executions.findByExecutionUid("exec-uid")).thenReturn(Optional.of(execRecord(5L, 1L)));
        writer.createExecution(new AuditExecutionStart("exec-uid", 1L, "conv", "req", T));
        verify(executions).save(any(AgentExecutionRecord.class)); // still only the first insert
    }

    @Test
    void addStep_skipsWhenExecutionMissing() {
        when(executions.findByExecutionUid("x")).thenReturn(Optional.empty());
        writer.addStep(new AuditStepEvent("x", 0, AgentStepKind.LLM_DECISION, AgentStepOutcome.OK,
                null, null, T, T, 1L));
        verify(steps, never()).save(any());
    }

    @Test
    void addStep_idempotentOnSequence() {
        when(executions.findByExecutionUid("x")).thenReturn(Optional.of(execRecord(7L, 1L)));
        when(steps.existsByExecutionIdAndSequence(7L, 0)).thenReturn(true);
        writer.addStep(new AuditStepEvent("x", 0, AgentStepKind.TOOL_CALL, AgentStepOutcome.OK,
                "task.search", null, T, T, 1L));
        verify(steps, never()).save(any());
    }

    @Test
    void addToolExecution_boundsResultSummary_andLinksStep() {
        when(toolExecutions.existsByToolExecutionUid("te-1")).thenReturn(false);
        when(executions.findByExecutionUid("x")).thenReturn(Optional.of(execRecord(7L, 42L)));
        AgentStepRecord step = new AgentStepRecordWithId(99L,
                new AgentStepRecord(7L, 3, AgentStepKind.TOOL_CALL, AgentStepOutcome.OK, "task.create", null, T, T, 1L));
        when(steps.findByExecutionIdOrderBySequenceAsc(7L)).thenReturn(List.of(step));

        writer.addToolExecution(new AuditToolEvent("te-1", "x", 3, "task.create",
                ToolRiskLevel.SIDE_EFFECTING, AgentToolOutcome.SUCCESS, null, "conf-1", "hash",
                "0123456789ABCDEFGHIJ", T, T, 5L)); // 20-char summary, cap is 10

        ArgumentCaptor<ToolExecutionRecord> captor = ArgumentCaptor.forClass(ToolExecutionRecord.class);
        verify(toolExecutions).save(captor.capture());
        ToolExecutionRecord saved = captor.getValue();
        assertThat(saved.getStepId()).isEqualTo(99L);
        assertThat(saved.getOwnerId()).isEqualTo(42L);
        assertThat(saved.getResultSummary()).hasSize(10); // bounded
        assertThat(saved.getConfirmationId()).isEqualTo("conf-1");
    }

    @Test
    void addToolExecution_idempotentOnUid() {
        when(toolExecutions.existsByToolExecutionUid("te-1")).thenReturn(true);
        writer.addToolExecution(new AuditToolEvent("te-1", "x", 3, "task.create",
                ToolRiskLevel.SIDE_EFFECTING, AgentToolOutcome.SUCCESS, null, null, null, null, T, T, 5L));
        verify(toolExecutions, never()).save(any());
    }

    @Test
    void completeExecution_mapsStatus_andBoundsFinalSummary() {
        AgentExecutionRecord rec = new AgentExecutionRecord("x", 1L, "c", "r",
                AuditExecutionStatus.STARTED, T);
        when(executions.findByExecutionUid("x")).thenReturn(Optional.of(rec));
        writer.completeExecution(new AuditExecutionEnd("x", AgentStatus.COMPLETED, null,
                "this-is-longer-than-ten", 3, 1, T, 12L));
        assertThat(rec.getStatus()).isEqualTo(AuditExecutionStatus.COMPLETED);
        assertThat(rec.getFinalResponseSummary()).hasSize(10);
        assertThat(rec.getToolCalls()).isEqualTo(1);
    }

    @Test
    void recordConfirmationExecution_appendsStepAndTool_andPromotes() {
        AgentExecutionRecord rec = new AgentExecutionRecordWithId(3L,
                new AgentExecutionRecord("x", 7L, "c", "r", AuditExecutionStatus.PENDING_CONFIRMATION, T));
        when(executions.findByExecutionUid("x")).thenReturn(Optional.of(rec));
        when(steps.maxSequence(3L)).thenReturn(4);
        when(steps.save(any())).thenReturn(new AgentStepRecordWithId(88L,
                new AgentStepRecord(3L, 5, AgentStepKind.CONFIRMATION_APPROVED, AgentStepOutcome.OK,
                        "task.create", null, T, T, 1L)));
        writer.recordConfirmationExecution(new com.prince.agentic.agent.AuditConfirmationExecuted(
                "x", "conf-1", "task.create", ToolRiskLevel.SIDE_EFFECTING, "hash", true, null,
                "{\"id\":1}", T, T.plusSeconds(1)));

        verify(steps).save(any());
        verify(toolExecutions).save(any());
        assertThat(rec.getStatus()).isEqualTo(AuditExecutionStatus.COMPLETED);
        assertThat(rec.getToolCalls()).isEqualTo(1);
    }

    @Test
    void recordConfirmationExecution_idempotentPerConfirmation() {
        AgentExecutionRecord rec = new AgentExecutionRecordWithId(3L,
                new AgentExecutionRecord("x", 7L, "c", "r", AuditExecutionStatus.PENDING_CONFIRMATION, T));
        when(executions.findByExecutionUid("x")).thenReturn(Optional.of(rec));
        when(toolExecutions.existsByExecutionIdAndConfirmationId(3L, "conf-1")).thenReturn(true);
        writer.recordConfirmationExecution(new com.prince.agentic.agent.AuditConfirmationExecuted(
                "x", "conf-1", "task.create", ToolRiskLevel.SIDE_EFFECTING, "hash", true, null, "r", T, T));
        verify(steps, never()).save(any());
        verify(toolExecutions, never()).save(any());
    }

    // --- tiny id-bearing subclasses so tests can supply a generated id without a DB ---
    private static final class AgentExecutionRecordWithId extends AgentExecutionRecord {
        private final long id;
        AgentExecutionRecordWithId(long id, AgentExecutionRecord src) {
            super(src.getExecutionUid(), src.getOwnerId(), src.getConversationId(), src.getRequestId(),
                    src.getStatus(), src.getStartedAt());
            this.id = id;
        }
        @Override public Long getId() { return id; }
    }

    private static final class AgentStepRecordWithId extends AgentStepRecord {
        private final long id;
        AgentStepRecordWithId(long id, AgentStepRecord src) {
            super(src.getExecutionId(), src.getSequence(), src.getStepType(), src.getStatus(),
                    src.getToolName(), src.getDetailCode(), src.getStartedAt(), src.getCompletedAt(),
                    src.getDurationMs());
            this.id = id;
        }
        @Override public Long getId() { return id; }
    }
}
