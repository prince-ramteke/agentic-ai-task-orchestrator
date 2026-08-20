package com.prince.agentic.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain exceptions that map cleanly to an HTTP response and the standard
 * {@link com.prince.agentic.common.response.ApiError} envelope.
 *
 * <p>Each subclass declares its HTTP status and a stable machine {@code code}. This keeps
 * error handling uniform: {@link GlobalExceptionHandler} renders any {@code ApiException}
 * the same way, so features add exceptions rather than error-rendering plumbing.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
