package com.prince.agentic.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard, machine-readable error envelope returned for every API error.
 *
 * <p>Contract (see docs/API.md, docs/ERROR_HANDLING.md):
 * <ul>
 *   <li>{@code error} is a stable machine code (e.g. {@code VALIDATION_ERROR}), not a
 *       human sentence — clients can branch on it.</li>
 *   <li>{@code message} is a human-safe summary. It never contains stack traces or
 *       internal implementation detail.</li>
 *   <li>{@code traceId} is a per-response identifier generated when the error is built,
 *       so a user can quote it in a support request. Full request-wide correlation-ID
 *       propagation across the agent/tool path is PLANNED for Milestone 10.</li>
 *   <li>{@code fieldErrors} is present only for validation failures (omitted otherwise).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        List<FieldValidationError> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path, String traceId) {
        return new ApiError(Instant.now(), status, error, message, path, traceId, null);
    }

    public static ApiError of(int status, String error, String message, String path, String traceId,
                              List<FieldValidationError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, traceId,
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
    }
}
