package com.prince.agentic.ai.llm.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Base type for LLM-layer failures.
 *
 * <p>Extends {@link ApiException} so the existing {@code GlobalExceptionHandler} renders these
 * through the standard {@code ApiError} envelope with no new handler and no second error system.
 * Each concrete subclass fixes its HTTP status and stable machine {@code code}.
 */
public abstract class LlmException extends ApiException {

    protected LlmException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
