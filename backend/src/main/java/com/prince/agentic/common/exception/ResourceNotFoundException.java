package com.prince.agentic.common.exception;

/**
 * Thrown when a requested resource does not exist (or is not visible to the caller).
 *
 * <p>Mapped centrally to HTTP 404 by {@link GlobalExceptionHandler}. This is the one
 * domain-agnostic exception the foundation needs now; feature-specific subtypes
 * (e.g. a task/customer not-found) are added by the milestones that introduce them.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
