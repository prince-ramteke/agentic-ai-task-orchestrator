package com.prince.agentic.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured result of one bounded agent run. {@code failureCode} is null on COMPLETED.
 *
 * <p>M7 adds {@code conversationId} — the server-minted id to continue this conversation (null when a
 * new conversation could not be persisted because memory was unavailable) — and {@code memoryStatus}
 * ({@code ACTIVE}/{@code UNAVAILABLE}).
 *
 * <p>M8 adds the confirmation fields, populated <b>only</b> when {@code status} is
 * {@code PENDING_CONFIRMATION}: {@code confirmationId} to confirm/cancel against, the {@code tool},
 * its {@code riskLevel}, a safe {@code summary}, and {@code expiresAt}. All fields are additive and
 * {@code NON_NULL}-omitted; existing M6/M7 fields are unchanged (published shape preserved).
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
        String memoryStatus,
        String confirmationId,
        String confirmationTool,
        String confirmationRiskLevel,
        String confirmationSummary,
        String confirmationExpiresAt) {
}
