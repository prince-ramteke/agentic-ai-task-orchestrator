package com.prince.agentic.agent;

import java.util.List;

/**
 * Structured outcome of one agent run. failureCode is null for COMPLETED (spec §15).
 *
 * <p>{@code observations} are the run's bounded, model-safe tool summaries — used by the M7 memory
 * layer to persist bounded TOOL turns. They are internal: the API response DTO never exposes them.
 */
public record AgentResult(
        String executionId,
        AgentStatus status,
        String finalResponse,
        int iterations,
        int toolCalls,
        long durationMs,
        String failureCode,
        List<AgentObservation> observations) {

    public AgentResult {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
