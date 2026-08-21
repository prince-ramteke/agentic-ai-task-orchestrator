package com.prince.agentic.common.exception;

import com.prince.agentic.common.response.ApiError;
import com.prince.agentic.common.response.FieldValidationError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Centralized translation of exceptions into the standard {@link ApiError} envelope.
 *
 * <p>Foundation for Milestone 1 (see docs/ERROR_HANDLING.md). It maps the exception
 * classes that already exist; feature/security/domain-specific exceptions are added by
 * the milestones that introduce them — this class is the reusable base, not a catalogue
 * of not-yet-existing error types.
 *
 * <p>Rules honoured here: no stack trace or internal detail is ever returned to the
 * client; 5xx are logged at ERROR with the traceId, 4xx at WARN; nothing sensitive is logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation failure on an {@code @Valid} request body → 400 with field details. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request validation failed.", request, fieldErrors);
    }

    /** Constraint violation on request params/path variables → 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new FieldValidationError(
                        v.getPropertyPath() == null ? null : v.getPropertyPath().toString(),
                        v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request validation failed.", request, fieldErrors);
    }

    /** A query/path param that can't be converted to its target type (e.g. bad enum value) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String field = ex.getName();
        String message = "Invalid value for parameter '" + field + "'.";
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request,
                List.of(new FieldValidationError(field, "invalid value")));
    }

    /** Unreadable/malformed request body (e.g. broken JSON) → 400, without echoing the payload. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body could not be read.", request, null);
    }

    /** Any domain {@link ApiException} → its declared status + machine code. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex,
                                                       HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null);
    }

    /**
     * Authorization denial raised during method invocation (e.g. {@code @PreAuthorize}) → 403.
     * Filter-chain-level denials are handled by {@code RestAccessDeniedHandler}; both render the
     * same envelope. This handler also prevents the generic 500 fallback from swallowing a 403.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to access this resource.", request, null);
    }

    /** Unknown route / missing resource → 404 in the standard envelope. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.", request, null);
    }

    /** Wrong HTTP method for an existing route → 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint.", request, null);
    }

    /** Fallback: anything unexpected → 500 with a safe message; full detail logged server-side only. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request, null, ex);
    }

    // --- helpers ---

    private FieldValidationError toFieldError(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null
                ? "invalid value" : fieldError.getDefaultMessage();
        return new FieldValidationError(fieldError.getField(), message);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request,
                                           List<FieldValidationError> fieldErrors) {
        return build(status, code, message, request, fieldErrors, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request,
                                           List<FieldValidationError> fieldErrors,
                                           Exception cause) {
        String traceId = UUID.randomUUID().toString();
        String path = request.getRequestURI();
        if (status.is5xxServerError()) {
            // Log the full cause server-side, keyed by traceId — never returned to the client.
            log.error("traceId={} status={} code={} path={}", traceId, status.value(), code, path, cause);
        } else {
            log.warn("traceId={} status={} code={} path={}", traceId, status.value(), code, path);
        }
        ApiError body = ApiError.of(status.value(), code, message, path, traceId, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
