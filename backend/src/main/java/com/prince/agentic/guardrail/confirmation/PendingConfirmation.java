package com.prince.agentic.guardrail.confirmation;

import com.prince.agentic.tool.ToolRiskLevel;

import java.time.Instant;

/**
 * The safe, client-facing view of a created confirmation (spec §6.3, §12). Exposes only what a caller
 * needs to confirm — never internal class names, arguments, prompts, or fingerprints.
 *
 * @param confirmationId opaque id to confirm/cancel against
 * @param tool           the tool that will run on confirm
 * @param riskLevel      the descriptor risk level (why confirmation is required)
 * @param summary        a short, safe human summary of the action
 * @param expiresAt      when the confirmation stops being valid
 */
public record PendingConfirmation(
        String confirmationId,
        String tool,
        ToolRiskLevel riskLevel,
        String summary,
        Instant expiresAt) {
}
