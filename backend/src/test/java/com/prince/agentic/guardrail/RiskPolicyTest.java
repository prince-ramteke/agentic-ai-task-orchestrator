package com.prince.agentic.guardrail;

import com.prince.agentic.agent.AgentAction;
import com.prince.agentic.agent.AgentDecision;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RiskPolicyTest {

    private final RiskPolicy policy = new RiskPolicy();
    private final AuthenticatedUser user = new AuthenticatedUser(1L, "a@b.com", Set.of("ROLE_USER"));

    private GuardrailInput inputFor(ToolRiskLevel risk) {
        AgentDecision decision = new AgentDecision(AgentAction.TOOL_CALL, null, "t.op", Map.of());
        return new GuardrailInput(user, decision,
                GuardrailTestTools.descriptor("t.op", risk), GuardrailContext.none());
    }

    @ParameterizedTest
    @EnumSource(value = ToolRiskLevel.class, names = {"READ_ONLY", "DETERMINISTIC"})
    void nonMutating_isAllowed(ToolRiskLevel risk) {
        assertThat(policy.evaluate(inputFor(risk)).outcome()).isEqualTo(GuardrailOutcome.ALLOW);
    }

    @ParameterizedTest
    @EnumSource(value = ToolRiskLevel.class, names = {"SIDE_EFFECTING", "HIGH_RISK"})
    void mutating_requiresConfirmation_andCarriesDescriptorRisk(ToolRiskLevel risk) {
        GuardrailDecision d = policy.evaluate(inputFor(risk));
        assertThat(d.outcome()).isEqualTo(GuardrailOutcome.REQUIRE_CONFIRMATION);
        assertThat(d.riskLevel()).isEqualTo(risk);
    }
}
