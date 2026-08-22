package com.prince.agentic.agent;

import com.prince.agentic.tool.ToolRiskLevel;

import java.time.Instant;

/**
 * Backend-observed fact: a confirmed action executed (spec §8). Recorded against the originating run
 * ({@code executionUid}); the recorder assigns the next step sequence, appends a
 * {@code CONFIRMATION_APPROVED} step + its tool execution, and promotes the run's status. Carries
 * {@code argumentsHash} (never raw args) and a bounded {@code resultSummary}; {@code confirmationId}
 * links the M8 confirmation (never its secret).
 */
public record AuditConfirmationExecuted(
        String executionUid,
        String confirmationId,
        String toolName,
        ToolRiskLevel riskLevel,
        String argumentsHash,
        boolean success,
        String errorCode,
        String resultSummary,
        Instant startedAt,
        Instant completedAt) {
}
