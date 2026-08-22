package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentStepKind;
import com.prince.agentic.agent.AgentStepOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Append-only audit record of one meaningful agent transition (spec §3.2). Ordered within an
 * execution by {@code sequence}; {@code UNIQUE(execution_id, sequence)} makes re-writes idempotent.
 * {@code detailCode} holds a stable reason/outcome code (e.g. a guardrail reasonCode) — never free
 * text, never chain-of-thought.
 */
@Entity
@Table(name = "agent_steps")
public class AgentStepRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private Long executionId;

    @Column(nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 24, updatable = false)
    private AgentStepKind stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private AgentStepOutcome status;

    @Column(name = "tool_name", length = 100, updatable = false)
    private String toolName;

    @Column(name = "detail_code", length = 48, updatable = false)
    private String detailCode;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", updatable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    protected AgentStepRecord() {
        // for JPA
    }

    public AgentStepRecord(Long executionId, int sequence, AgentStepKind stepType, AgentStepOutcome status,
                           String toolName, String detailCode, Instant startedAt, Instant completedAt,
                           Long durationMs) {
        this.executionId = executionId;
        this.sequence = sequence;
        this.stepType = stepType;
        this.status = status;
        this.toolName = toolName;
        this.detailCode = detailCode;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    public Long getId() { return id; }
    public Long getExecutionId() { return executionId; }
    public int getSequence() { return sequence; }
    public AgentStepKind getStepType() { return stepType; }
    public AgentStepOutcome getStatus() { return status; }
    public String getToolName() { return toolName; }
    public String getDetailCode() { return detailCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getDurationMs() { return durationMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentStepRecord r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
