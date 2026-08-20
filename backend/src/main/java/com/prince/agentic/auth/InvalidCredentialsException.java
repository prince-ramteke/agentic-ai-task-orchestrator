package com.prince.agentic.auth;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Raised on any failed login → HTTP 401 with a single generic message, so the response never
 * reveals whether the email exists, is disabled, or the password was wrong (no enumeration).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
    }
}
