package com.prince.agentic.guardrail.exception;

import org.springframework.http.HttpStatus;

/** A confirmation was found but its TTL had elapsed (spec §6.3). {@code 410 CONFIRMATION_EXPIRED}. */
public class ConfirmationExpiredException extends GuardrailException {

    public ConfirmationExpiredException() {
        super(HttpStatus.GONE, "CONFIRMATION_EXPIRED", "This confirmation has expired.");
    }
}
