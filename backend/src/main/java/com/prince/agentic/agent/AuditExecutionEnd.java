package com.prince.agentic.agent;

import java.time.Instant;

/**
 * Backend-observed fact: an agent run reached a terminal state (spec §8). {@code finalResponseSummary}
 * is a bounded, length-capped, redacted summary — the recorder caps it; it is never a raw prompt or
 * chain-of-thought. On a later successful confirm, this is emitted again to promote a
 * {@code PENDING_CONFIRMATION} execution to {@code COMPLETED}/{@code FAILED}.
 */
public record AuditExecutionEnd(
        String executionUid,
        AgentStatus status,
        String failureCode,
        String finalResponseSummary,
        int iterations,
        int toolCalls,
        Instant completedAt,
        Long durationMs) {
}
