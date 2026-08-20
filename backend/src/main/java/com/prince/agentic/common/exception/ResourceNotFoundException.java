package com.prince.agentic.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist (or is not visible to the caller).
 * Renders as HTTP 404 with code {@code NOT_FOUND} via {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
