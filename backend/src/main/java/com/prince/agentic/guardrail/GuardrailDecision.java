package com.prince.agentic.guardrail;

import com.prince.agentic.tool.ToolRiskLevel;

/**
 * Immutable result of a guardrail evaluation (spec §3). {@code riskLevel} always comes from the tool
 * descriptor (never from the model). {@code message} is short and safe — no internals, prompts, or
 * secrets. Build via the factories so an inconsistent decision cannot exist.
 *
 * @param outcome    ALLOW / DENY / REQUIRE_CONFIRMATION
 * @param reasonCode stable machine code (e.g. {@code CONFIRMATION_REQUIRED}, {@code UNSAFE_ACTION})
 * @param message    safe human-readable summary (may be null for ALLOW)
 * @param riskLevel  the tool's descriptor risk (may be null when no tool is involved)
 * @param policyId   which policy produced this outcome (for logs/metrics; null for a plain ALLOW)
 */
public record GuardrailDecision(
        GuardrailOutcome outcome,
        String reasonCode,
        String message,
        ToolRiskLevel riskLevel,
        String policyId) {

    public boolean isAllowed() {
        return outcome == GuardrailOutcome.ALLOW;
    }

    public boolean requiresConfirmation() {
        return outcome == GuardrailOutcome.REQUIRE_CONFIRMATION;
    }

    public static GuardrailDecision allow() {
        return new GuardrailDecision(GuardrailOutcome.ALLOW, "ALLOWED", null, null, null);
    }

    /**
     * ALLOW enriched with the tool's descriptor risk (M9 audit): the outcome is identical to
     * {@link #allow()}, but the resolved {@link ToolRiskLevel} is carried through so a caller can
     * record the executed tool's risk without re-resolving the descriptor.
     */
    public static GuardrailDecision allowWithRisk(ToolRiskLevel riskLevel) {
        return new GuardrailDecision(GuardrailOutcome.ALLOW, "ALLOWED", null, riskLevel, null);
    }

    public static GuardrailDecision deny(String reasonCode, String message, String policyId) {
        return new GuardrailDecision(GuardrailOutcome.DENY, reasonCode, message, null, policyId);
    }

    public static GuardrailDecision requireConfirmation(ToolRiskLevel riskLevel, String policyId) {
        return new GuardrailDecision(GuardrailOutcome.REQUIRE_CONFIRMATION, "CONFIRMATION_REQUIRED",
                "This action requires confirmation before it can run.", riskLevel, policyId);
    }
}
