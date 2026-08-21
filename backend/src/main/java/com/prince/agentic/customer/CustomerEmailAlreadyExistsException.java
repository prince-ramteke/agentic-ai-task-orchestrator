package com.prince.agentic.customer;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** The authenticated user already has a customer with this email → 409 CONFLICT. */
public class CustomerEmailAlreadyExistsException extends ApiException {
    public CustomerEmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, "CONFLICT", "A customer with this email already exists.");
    }
}
