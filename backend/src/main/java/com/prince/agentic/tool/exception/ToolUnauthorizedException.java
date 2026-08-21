package com.prince.agentic.tool.exception;

import org.springframework.http.HttpStatus;

/** An auth-required tool was invoked without an authenticated principal. {@code 401 TOOL_UNAUTHORIZED}. */
public class ToolUnauthorizedException extends ToolException {

    public ToolUnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "TOOL_UNAUTHORIZED", message);
    }
}
