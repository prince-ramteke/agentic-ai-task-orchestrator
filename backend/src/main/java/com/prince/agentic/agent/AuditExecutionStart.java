package com.prince.agentic.agent;

import java.time.Instant;

/**
 * Backend-observed fact: an agent run started (spec §8). Carries correlation ids only — never prompt
 * text. {@code conversationId} is metadata, never an authorization claim.
 */
public record AuditExecutionStart(
        String executionUid,
        long ownerId,
        String conversationId,
        String requestId,
        Instant startedAt) {
}
