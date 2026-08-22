package com.prince.agentic.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of confirming and executing a pending action (M8). {@code status} is {@code EXECUTED} on
 * success or {@code FAILED} when the tool itself failed (with a stable {@code errorCode}).
 * {@code resultSummary} is the bounded, model-safe observation summary — never raw entity/internal
 * data. No arguments, fingerprints, or internal class names are exposed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentConfirmResponse(
        String confirmationId,
        String tool,
        String status,
        String resultSummary,
        String errorCode) {
}
