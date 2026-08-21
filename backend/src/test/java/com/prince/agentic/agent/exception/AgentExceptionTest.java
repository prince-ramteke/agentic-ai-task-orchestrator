package com.prince.agentic.agent.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExceptionTest {
    @Test
    void invalidDecision_maps_to_422_with_stable_code() {
        AgentInvalidDecisionException ex = new AgentInvalidDecisionException("bad");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getCode()).isEqualTo("AGENT_INVALID_DECISION");
    }
}
