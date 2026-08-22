package com.prince.agentic.guardrail.confirmation;

import com.prince.agentic.tool.ToolRiskLevel;

import java.util.Map;

/**
 * The exact stored action returned by a successful, single-use confirm (spec §6.3). The caller
 * executes precisely these tool + arguments through the normal {@code ToolExecutor} gates — the
 * confirmation authorizes intent, it never bypasses authorization.
 */
public record ConfirmedAction(String executionId, String toolName, Map<String, Object> arguments,
                              ToolRiskLevel riskLevel) {

    public ConfirmedAction {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
