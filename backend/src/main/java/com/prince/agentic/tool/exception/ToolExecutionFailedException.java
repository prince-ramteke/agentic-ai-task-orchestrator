package com.prince.agentic.tool.exception;

import org.springframework.http.HttpStatus;

/**
 * An unexpected failure while executing a tool (not a domain {@code ApiException}, which is surfaced
 * with its own code). {@code 500 TOOL_EXECUTION_FAILED}. The cause is attached for server-side
 * logging and never returned to the caller.
 */
public class ToolExecutionFailedException extends ToolException {

    public ToolExecutionFailedException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "TOOL_EXECUTION_FAILED", message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
