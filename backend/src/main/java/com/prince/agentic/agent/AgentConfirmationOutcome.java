package com.prince.agentic.agent;

/**
 * The outcome of executing a confirmed action (M8). Safe by construction: {@code resultSummary} is the
 * bounded, model-safe observation summary (via {@code ObservationSerializer}) — never raw entity or
 * internal data. {@code errorCode} is the stable tool error code on failure, else null.
 */
public record AgentConfirmationOutcome(
        String confirmationId,
        String tool,
        boolean success,
        String resultSummary,
        String errorCode) {
}
