package com.prince.agentic.guardrail;

import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

/**
 * The core risk policy (spec §4, §7). The tool descriptor's {@link ToolRiskLevel} is authoritative
 * and the model can never downgrade it:
 *
 * <pre>
 *   READ_ONLY, DETERMINISTIC → ALLOW
 *   SIDE_EFFECTING, HIGH_RISK → REQUIRE_CONFIRMATION
 * </pre>
 */
@Component
public class RiskPolicy implements GuardrailPolicy {

    static final int ORDER = 20;
    private static final String POLICY_ID = "risk";

    @Override
    public GuardrailDecision evaluate(GuardrailInput input) {
        ToolRiskLevel risk = input.descriptor().risk();
        return switch (risk) {
            case READ_ONLY, DETERMINISTIC -> GuardrailDecision.allow();
            case SIDE_EFFECTING, HIGH_RISK -> GuardrailDecision.requireConfirmation(risk, POLICY_ID);
        };
    }

    @Override
    public int order() {
        return ORDER;
    }
}
