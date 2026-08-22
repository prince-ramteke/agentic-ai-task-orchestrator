package com.prince.agentic.memory;

import java.time.Instant;

/**
 * One bounded conversational turn stored in memory (spec §2). {@code content} is a plain string:
 * a user message, an assistant final response, or a bounded tool-observation summary — never an
 * entity graph, a JWT, a security context, or raw {@code ToolResult} internals. {@code tool} is
 * set only for {@link MemoryRole#TOOL} messages (the tool name), otherwise {@code null}.
 */
public record MemoryMessage(
        MemoryRole role,
        String content,
        String tool,
        int sequence,
        Instant timestamp) {

    public static MemoryMessage user(String content, int sequence, Instant at) {
        return new MemoryMessage(MemoryRole.USER, content, null, sequence, at);
    }

    public static MemoryMessage assistant(String content, int sequence, Instant at) {
        return new MemoryMessage(MemoryRole.ASSISTANT, content, null, sequence, at);
    }

    public static MemoryMessage tool(String tool, String summary, int sequence, Instant at) {
        return new MemoryMessage(MemoryRole.TOOL, summary, tool, sequence, at);
    }
}
