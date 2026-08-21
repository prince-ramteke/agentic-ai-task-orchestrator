package com.prince.agentic.ai.llm.exception;

import org.springframework.http.HttpStatus;

/**
 * The model produced output that parsed but failed application-side validation, even after the
 * single bounded repair attempt. Model output is untrusted: parsing is not acceptance. Maps to
 * {@code 422 UNPROCESSABLE_ENTITY} / {@code LLM_INVALID_OUTPUT}.
 */
public class LlmInvalidOutputException extends LlmException {

    public LlmInvalidOutputException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "LLM_INVALID_OUTPUT", message);
    }
}
