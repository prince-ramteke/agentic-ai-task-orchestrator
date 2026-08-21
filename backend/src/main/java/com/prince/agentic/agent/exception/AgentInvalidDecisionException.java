package com.prince.agentic.agent.exception;

import org.springframework.http.HttpStatus;

/** The model produced an unparseable/invalid decision after one bounded repair (spec §6, §15). */
public class AgentInvalidDecisionException extends AgentException {
    public AgentInvalidDecisionException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "AGENT_INVALID_DECISION", message);
    }
}
