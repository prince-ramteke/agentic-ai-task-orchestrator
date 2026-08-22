package com.prince.agentic.audit.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * An audited execution does not exist or is not owned by the authenticated user (spec §11). A single
 * masked 404 for both, so a foreign or guessed execution id never reveals another user's history
 * (existence-masking, matching the M3/M5/M7 ownership convention).
 */
public class ExecutionNotFoundException extends ApiException {

    public ExecutionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", "Execution not found.");
    }
}
