package com.prince.agentic.tool;

/**
 * The outcome of a tool execution, produced by the {@link ToolExecutor}. Success carries typed
 * {@code data}; failure carries a stable {@link ToolError}. Either way {@code durationMs} is the
 * measured wall-clock time. This structured envelope is what the future agent (M6) feeds back as an
 * observation.
 *
 * @param <O> the tool's output type
 */
public record ToolResult<O>(
        String toolName,
        boolean success,
        O data,
        ToolError error,
        long durationMs) {

    public static <O> ToolResult<O> ok(String toolName, O data, long durationMs) {
        return new ToolResult<>(toolName, true, data, null, durationMs);
    }

    public static <O> ToolResult<O> failure(String toolName, ToolError error, long durationMs) {
        return new ToolResult<>(toolName, false, null, error, durationMs);
    }
}
