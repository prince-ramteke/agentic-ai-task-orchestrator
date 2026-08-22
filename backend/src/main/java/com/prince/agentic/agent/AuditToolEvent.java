package com.prince.agentic.agent;

import com.prince.agentic.tool.ToolRiskLevel;

/**
 * Backend-observed fact: a real tool execution (spec §8), linked to its {@code TOOL_CALL} step by
 * {@code stepSequence}. Carries {@code argumentsHash} (SHA-256 of canonical args) and a bounded
 * {@code resultSummary} — never raw arguments or raw results. {@code confirmationId} links a confirmed
 * action (never the confirmation secret).
 */
public record AuditToolEvent(
        String toolExecutionUid,
        String executionUid,
        int stepSequence,
        String toolName,
        ToolRiskLevel riskLevel,
        AgentToolOutcome outcome,
        String errorCode,
        String confirmationId,
        String argumentsHash,
        String resultSummary,
        java.time.Instant startedAt,
        java.time.Instant completedAt,
        Long durationMs) {
}
