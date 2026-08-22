package com.prince.agentic.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured result of one bounded agent run. {@code failureCode} is null on COMPLETED.
 *
 * <p>M7 adds {@code conversationId} — the server-minted id to continue this conversation (null when a
 * new conversation could not be persisted because memory was unavailable) — and {@code memoryStatus}
 * ({@code ACTIVE}/{@code UNAVAILABLE}). Both fields are additive; existing M6 fields are unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExecuteResponse(
        String executionId,
        String status,
        String response,
        int iterations,
        int toolCalls,
        long durationMs,
        String failureCode,
        String conversationId,
        String memoryStatus) {
}
