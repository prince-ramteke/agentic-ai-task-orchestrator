package com.prince.agentic.agent;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AgentDecisionValidatorTest {
    private final AgentDecisionValidator v = new AgentDecisionValidator();

    @Test void finalWithResponse_isValid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.FINAL, "hi", null, null))).isTrue();
    }
    @Test void finalWithoutResponse_isInvalid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.FINAL, "  ", null, null))).isFalse();
    }
    @Test void finalWithTool_isInvalid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.FINAL, "hi", "task.get", null))).isFalse();
    }
    @Test void toolCallWithTool_isValid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("page", 0)))).isTrue();
    }
    @Test void toolCallWithEmptyArgs_isValid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", null))).isTrue();
    }
    @Test void toolCallWithResponse_isInvalid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.TOOL_CALL, "answer", "task.search", Map.of()))).isFalse();
    }
    @Test void toolCallWithoutTool_isInvalid() {
        assertThat(v.isValid(new AgentDecision(AgentAction.TOOL_CALL, null, " ", Map.of()))).isFalse();
    }
    @Test void nullDecisionOrAction_isInvalid() {
        assertThat(v.isValid(null)).isFalse();
        assertThat(v.isValid(new AgentDecision(null, "x", null, null))).isFalse();
    }
}
