package com.prince.agentic.tool.exception;

import org.springframework.http.HttpStatus;

/** No tool is registered under the requested name. {@code 404 TOOL_NOT_FOUND}. */
public class ToolNotFoundException extends ToolException {

    public ToolNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", message);
    }
}
