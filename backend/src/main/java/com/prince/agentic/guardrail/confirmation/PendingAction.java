package com.prince.agentic.guardrail.confirmation;

import com.prince.agentic.tool.ToolRiskLevel;

import java.util.Map;

/**
 * The exact action the agent proposed but did not execute because a guardrail required confirmation
 * (spec §6, §11). Produced by the orchestrator and carried out of the run; the conversation layer
 * turns it into a stored, fingerprint-bound {@link Confirmation}. Arguments are the validated
 * model-proposed arguments — never re-supplied by a client at confirm time.
 *
 * <p>{@code executionId} (M9) is the originating agent run's id, carried so the later confirm can be
 * audited against the correct execution. It is <b>not</b> part of the confirmation fingerprint (the
 * fingerprint binds identity of the action, not the run).
 */
public record PendingAction(String executionId, String tool, Map<String, Object> arguments,
                            ToolRiskLevel riskLevel) {

    public PendingAction {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
