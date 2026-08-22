package com.prince.agentic.guardrail.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Base for guardrail/confirmation failures (spec §13). Each subclass declares its HTTP status and a
 * stable machine code, so {@code GlobalExceptionHandler} renders it in the standard {@code ApiError}
 * envelope with no special-casing. No internal policy detail is ever put in the client message.
 */
public abstract class GuardrailException extends ApiException {

    protected GuardrailException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
