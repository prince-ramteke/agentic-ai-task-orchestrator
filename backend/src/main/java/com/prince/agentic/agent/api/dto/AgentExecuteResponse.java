package com.prince.agentic.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Structured result of one bounded agent run. {@code failureCode} is null on COMPLETED. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExecuteResponse(
        String executionId,
        String status,
        String response,
        int iterations,
        int toolCalls,
        long durationMs,
        String failureCode) {
}
