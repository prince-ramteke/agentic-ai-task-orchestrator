package com.prince.agentic.tool.exception;

/**
 * Thrown at startup when the tool registry is invalid (duplicate name, malformed metadata, invalid
 * role, null handler). It is a plain {@link RuntimeException} — not an {@code ApiException} — because
 * it must <b>fail application boot</b> (fail-fast), not render an HTTP response. Machine label:
 * {@code TOOL_REGISTRATION_ERROR}.
 */
public class ToolRegistrationException extends RuntimeException {

    public ToolRegistrationException(String message) {
        super(message);
    }
}
