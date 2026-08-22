package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentToolOutcome;
import com.prince.agentic.tool.ToolRiskLevel;
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
 * Append-only durable record of a real tool execution (spec §3.3). {@code tool_execution_uid} is the
 * idempotency natural key. Stores safe metadata + {@code arguments_hash} (SHA-256 of canonical args)
 * + a bounded {@code result_summary} — never raw arguments/results. {@code owner_id}/{@code execution_id}
 * are denormalized for owner-scoped joins; {@code confirmation_id} links a confirmed action (never the
 * confirmation secret).
 */
@Entity
@Table(name = "tool_executions")
public class ToolExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tool_execution_uid", nullable = false, updatable = false, length = 36)
    private String toolExecutionUid;

    @Column(name = "step_id", nullable = false, updatable = false)
    private Long stepId;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private Long executionId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "tool_name", nullable = false, length = 100, updatable = false)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16, updatable = false)
    private ToolRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    private AgentToolOutcome outcome;

    @Column(name = "error_code", length = 48, updatable = false)
    private String errorCode;

    @Column(name = "confirmation_id", length = 36, updatable = false)
    private String confirmationId;

    @Column(name = "arguments_hash", length = 64, updatable = false)
    private String argumentsHash;

    @Column(name = "result_summary", length = 600, updatable = false)
    private String resultSummary;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", updatable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    protected ToolExecutionRecord() {
        // for JPA
    }

    public ToolExecutionRecord(String toolExecutionUid, Long stepId, Long executionId, Long ownerId,
                               String toolName, ToolRiskLevel riskLevel, AgentToolOutcome outcome,
                               String errorCode, String confirmationId, String argumentsHash,
                               String resultSummary, Instant startedAt, Instant completedAt,
                               Long durationMs) {
        this.toolExecutionUid = toolExecutionUid;
        this.stepId = stepId;
        this.executionId = executionId;
        this.ownerId = ownerId;
        this.toolName = toolName;
        this.riskLevel = riskLevel;
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.confirmationId = confirmationId;
        this.argumentsHash = argumentsHash;
        this.resultSummary = resultSummary;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    public Long getId() { return id; }
    public String getToolExecutionUid() { return toolExecutionUid; }
    public Long getStepId() { return stepId; }
    public Long getExecutionId() { return executionId; }
    public Long getOwnerId() { return ownerId; }
    public String getToolName() { return toolName; }
    public ToolRiskLevel getRiskLevel() { return riskLevel; }
    public AgentToolOutcome getOutcome() { return outcome; }
    public String getErrorCode() { return errorCode; }
    public String getConfirmationId() { return confirmationId; }
    public String getArgumentsHash() { return argumentsHash; }
    public String getResultSummary() { return resultSummary; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getDurationMs() { return durationMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToolExecutionRecord r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
