package com.prince.agentic.agent.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Base for agent-layer faults that map to the standard ApiError envelope. */
public abstract class AgentException extends ApiException {
    protected AgentException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
