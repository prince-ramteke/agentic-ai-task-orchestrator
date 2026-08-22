package com.prince.agentic.guardrail.exception;

import org.springframework.http.HttpStatus;

/**
 * A stored confirmation's recomputed fingerprint did not match the stored one — the record's bound
 * fields were tampered with (spec §6.3). {@code 409 CONFIRMATION_MISMATCH}.
 */
public class ConfirmationMismatchException extends GuardrailException {

    public ConfirmationMismatchException() {
        super(HttpStatus.CONFLICT, "CONFIRMATION_MISMATCH", "This confirmation is no longer valid.");
    }
}
