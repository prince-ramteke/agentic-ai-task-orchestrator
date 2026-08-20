package com.prince.agentic.auth;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised when registration targets an email that already exists → HTTP 409.
 */
public class EmailAlreadyExistsException extends ApiException {

    public EmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
    }
}
