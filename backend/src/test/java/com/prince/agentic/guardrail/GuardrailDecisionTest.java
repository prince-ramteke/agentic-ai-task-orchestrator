package com.prince.agentic.guardrail;

import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailDecisionTest {

    @Test
    void allow_isAllowedAndCarriesNoRisk() {
        GuardrailDecision d = GuardrailDecision.allow();
        assertThat(d.outcome()).isEqualTo(GuardrailOutcome.ALLOW);
        assertThat(d.isAllowed()).isTrue();
        assertThat(d.requiresConfirmation()).isFalse();
        assertThat(d.riskLevel()).isNull();
    }

    @Test
    void deny_carriesCodeAndPolicyAndIsNotAllowed() {
        GuardrailDecision d = GuardrailDecision.deny("UNSAFE_ACTION", "no", "arg-safety");
        assertThat(d.outcome()).isEqualTo(GuardrailOutcome.DENY);
        assertThat(d.isAllowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("UNSAFE_ACTION");
        assertThat(d.policyId()).isEqualTo("arg-safety");
    }

    @Test
    void requireConfirmation_carriesDescriptorRisk() {
        GuardrailDecision d = GuardrailDecision.requireConfirmation(ToolRiskLevel.HIGH_RISK, "risk");
        assertThat(d.requiresConfirmation()).isTrue();
        assertThat(d.reasonCode()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(d.riskLevel()).isEqualTo(ToolRiskLevel.HIGH_RISK);
    }
}
