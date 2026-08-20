package com.prince.agentic.common.response;

/**
 * A single field-level validation failure, surfaced inside {@link ApiError#fieldErrors()}.
 *
 * @param field   the offending field/parameter name
 * @param message the validation message (safe for clients)
 */
public record FieldValidationError(String field, String message) {
}
