package com.prince.agentic.tool.exception;

import org.springframework.http.HttpStatus;

/** The principal lacks a role required to use the tool (tool-type authorization). {@code 403 TOOL_FORBIDDEN}. */
public class ToolForbiddenException extends ToolException {

    public ToolForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "TOOL_FORBIDDEN", message);
    }
}
