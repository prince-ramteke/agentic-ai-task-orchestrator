package com.prince.agentic.tool.exception;

import org.springframework.http.HttpStatus;

/**
 * Reserved for hard timeout enforcement. In M5 timeout is metadata + measured duration only; hard
 * cancellation is designed with guardrails in M8. {@code 504 TOOL_TIMEOUT}.
 */
public class ToolTimeoutException extends ToolException {

    public ToolTimeoutException(String message) {
        super(HttpStatus.GATEWAY_TIMEOUT, "TOOL_TIMEOUT", message);
    }
}
