package com.prince.agentic.guardrail.exception;

import org.springframework.http.HttpStatus;

/**
 * A confirmation was already consumed by a prior, successful confirm (spec §6.3, §15). Single-use is
 * enforced atomically; a replay or a losing concurrent attempt lands here or on a masked 404.
 * {@code 409 CONFIRMATION_ALREADY_USED}.
 */
public class ConfirmationAlreadyUsedException extends GuardrailException {

    public ConfirmationAlreadyUsedException() {
        super(HttpStatus.CONFLICT, "CONFIRMATION_ALREADY_USED", "This confirmation was already used.");
    }
}
