package com.prince.agentic.tool;

/**
 * A safe, stable error inside a {@link ToolResult}: a machine code plus a human-safe message.
 * Never contains a stack trace or an internal Java class name.
 */
public record ToolError(String code, String message) {}
