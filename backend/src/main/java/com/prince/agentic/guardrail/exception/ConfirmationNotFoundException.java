package com.prince.agentic.guardrail.exception;

import org.springframework.http.HttpStatus;

/**
 * A confirmation does not exist, was already consumed, or is not owned by the caller (spec §6.3).
 * A single masked 404 for all three so a foreign or guessed id never reveals another user's pending
 * action (existence-masking, matching the M5/M7 ownership convention).
 */
public class ConfirmationNotFoundException extends GuardrailException {

    public ConfirmationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CONFIRMATION_NOT_FOUND", "Confirmation not found.");
    }
}
