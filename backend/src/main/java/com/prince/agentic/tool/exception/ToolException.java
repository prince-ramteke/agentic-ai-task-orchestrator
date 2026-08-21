package com.prince.agentic.tool.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Base for tool-layer failures. Extends {@link ApiException} so, if one ever surfaces over HTTP, the
 * existing {@code GlobalExceptionHandler} renders it through the standard {@code ApiError} envelope
 * with no new plumbing. Within the {@link com.prince.agentic.tool.ToolExecutor} these are caught and
 * projected into a {@link com.prince.agentic.tool.ToolResult} failure carrying the stable code.
 */
public abstract class ToolException extends ApiException {

    protected ToolException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
