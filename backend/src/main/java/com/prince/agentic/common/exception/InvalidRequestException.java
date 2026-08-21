package com.prince.agentic.common.exception;

import org.springframework.http.HttpStatus;

/** A malformed but well-formed-JSON request parameter (e.g. an unknown sort field) → 400. */
public class InvalidRequestException extends ApiException {
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
