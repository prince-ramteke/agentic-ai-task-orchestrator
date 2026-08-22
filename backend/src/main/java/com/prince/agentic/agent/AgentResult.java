package com.prince.agentic.agent;

import com.prince.agentic.guardrail.confirmation.PendingAction;

import java.util.List;

/**
 * Structured outcome of one agent run. failureCode is null for COMPLETED (spec §15).
 *
 * <p>{@code observations} are the run's bounded, model-safe tool summaries — used by the M7 memory
 * layer to persist bounded TOOL turns. They are internal: the API response DTO never exposes them.
 *
 * <p>{@code pending} (M8) is non-null only for {@code PENDING_CONFIRMATION}: it is the exact
 * side-effecting action the guardrail halted, which the conversation layer turns into a stored,
 * fingerprint-bound confirmation. It is internal — the API exposes only a safe confirmation view.
 */
public record AgentResult(
        String executionId,
        AgentStatus status,
        String finalResponse,
        int iterations,
        int toolCalls,
        long durationMs,
        String failureCode,
        List<AgentObservation> observations,
        PendingAction pending) {

    public AgentResult {
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    /** Backward-compatible constructor for the non-confirmation terminals (pending = null). */
    public AgentResult(String executionId, AgentStatus status, String finalResponse, int iterations,
                       int toolCalls, long durationMs, String failureCode,
                       List<AgentObservation> observations) {
        this(executionId, status, finalResponse, iterations, toolCalls, durationMs, failureCode,
                observations, null);
    }
}
