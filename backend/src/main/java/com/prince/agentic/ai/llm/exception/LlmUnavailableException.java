package com.prince.agentic.ai.llm.exception;

import org.springframework.http.HttpStatus;

/**
 * The LLM provider could not be reached (connection refused, host down) or the requested model is
 * not present. Maps to {@code 503 SERVICE_UNAVAILABLE} / {@code LLM_UNAVAILABLE}.
 */
public class LlmUnavailableException extends LlmException {

    public LlmUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", message);
    }
}
