package com.prince.agentic.agent;

import java.time.Instant;

/**
 * Backend-observed fact: one meaningful agent step (spec §8). {@code detailCode} is a stable code
 * (e.g. a guardrail reasonCode or failureCode) — never free text or chain-of-thought.
 */
public record AuditStepEvent(
        String executionUid,
        int sequence,
        AgentStepKind kind,
        AgentStepOutcome outcome,
        String toolName,
        String detailCode,
        Instant startedAt,
        Instant completedAt,
        Long durationMs) {
}
