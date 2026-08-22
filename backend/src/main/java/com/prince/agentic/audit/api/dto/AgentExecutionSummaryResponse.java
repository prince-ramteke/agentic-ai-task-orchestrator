package com.prince.agentic.audit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Safe list-row view of one audited execution (spec §12, §22). Exposes only metadata — no internal
 * class names, prompts, arguments, LLM output, chain-of-thought, or secrets.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExecutionSummaryResponse(
        String executionId,
        String status,
        String conversationId,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        int iterations,
        int toolCalls,
        String failureCode) {
}
