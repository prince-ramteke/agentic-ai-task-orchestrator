package com.prince.agentic.customer;

import com.prince.agentic.common.exception.ResourceNotFoundException;

/** A customer does not exist or is not visible to the caller — both render as 404 (existence-masking). */
public class CustomerNotFoundException extends ResourceNotFoundException {
    public CustomerNotFoundException(Long id) {
        super("Customer not found: " + id);
    }
}
