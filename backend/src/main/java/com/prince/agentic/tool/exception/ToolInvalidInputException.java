package com.prince.agentic.tool.exception;

import org.springframework.http.HttpStatus;

/**
 * The tool arguments could not be bound to the typed input or failed validation (including an
 * unknown/spoofed property). {@code 400 TOOL_INVALID_INPUT}.
 */
public class ToolInvalidInputException extends ToolException {

    public ToolInvalidInputException(String message) {
        super(HttpStatus.BAD_REQUEST, "TOOL_INVALID_INPUT", message);
    }
}
