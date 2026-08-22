package com.prince.agentic.guardrail.exception;

import org.springframework.http.HttpStatus;

/**
 * A guardrail policy denied an action outright (spec §13). Used on the direct-execution paths where a
 * denial must surface as an HTTP error rather than an agent observation. {@code 403 GUARDRAIL_DENIED}.
 */
public class GuardrailDeniedException extends GuardrailException {

    public GuardrailDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, "GUARDRAIL_DENIED",
                message == null ? "This action was blocked by a safety policy." : message);
    }
}
