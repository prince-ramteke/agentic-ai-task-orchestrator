package com.prince.agentic.ai.llm.exception;

import org.springframework.http.HttpStatus;

/**
 * The LLM request exceeded the configured read timeout. Maps to {@code 504 GATEWAY_TIMEOUT} /
 * {@code LLM_TIMEOUT}. A slow local model is expected occasionally; the bounded wait fails fast
 * rather than hanging the request thread indefinitely.
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message) {
        super(HttpStatus.GATEWAY_TIMEOUT, "LLM_TIMEOUT", message);
    }
}
