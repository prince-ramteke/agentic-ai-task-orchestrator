package com.prince.agentic.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable audit record for one agent run (spec §3.1). Owner is a plain {@code owner_id} FK column
 * (like {@code Task}); {@code execution_uid} is the run's UUID and the idempotency natural key.
 * The row is created {@code STARTED} and its lifecycle fields (status/completedAt/counts/summary) are
 * updated to terminal at completion — steps/tool rows are append-only, but the execution row is the
 * single mutable lifecycle anchor. Stores safe metadata only; {@code finalResponseSummary} is bounded.
 */
@Entity
@Table(name = "agent_executions")
public class AgentExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_uid", nullable = false, updatable = false, length = 36)
    private String executionUid;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "conversation_id", updatable = false, length = 36)
    private String conversationId;

    @Column(name = "request_id", updatable = false, length = 36)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuditExecutionStatus status;

    @Column(nullable = false)
    private int iterations;

    @Column(name = "tool_calls", nullable = false)
    private int toolCalls;

    @Column(name = "failure_code", length = 48)
    private String failureCode;

    @Column(name = "final_response_summary", length = 600)
    private String finalResponseSummary;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentExecutionRecord() {
        // for JPA
    }

    public AgentExecutionRecord(String executionUid, Long ownerId, String conversationId,
                                String requestId, AuditExecutionStatus status, Instant startedAt) {
        this.executionUid = executionUid;
        this.ownerId = ownerId;
        this.conversationId = conversationId;
        this.requestId = requestId;
        this.status = status;
        this.startedAt = startedAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Terminal (or confirm-promoted) lifecycle update. */
    public void complete(AuditExecutionStatus status, String failureCode, String finalResponseSummary,
                         int iterations, int toolCalls, Instant completedAt, Long durationMs) {
        this.status = status;
        this.failureCode = failureCode;
        this.finalResponseSummary = finalResponseSummary;
        this.iterations = iterations;
        this.toolCalls = toolCalls;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    public Long getId() { return id; }
    public String getExecutionUid() { return executionUid; }
    public Long getOwnerId() { return ownerId; }
    public String getConversationId() { return conversationId; }
    public String getRequestId() { return requestId; }
    public AuditExecutionStatus getStatus() { return status; }
    public int getIterations() { return iterations; }
    public int getToolCalls() { return toolCalls; }
    public String getFailureCode() { return failureCode; }
    public String getFinalResponseSummary() { return finalResponseSummary; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentExecutionRecord r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
