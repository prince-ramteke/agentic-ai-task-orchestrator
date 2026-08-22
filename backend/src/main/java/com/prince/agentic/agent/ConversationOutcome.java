package com.prince.agentic.agent;

/**
 * The result of one conversational agent turn: the bounded run outcome plus the memory metadata the
 * API surfaces. {@code conversationId} is the server-minted id to continue the conversation, or
 * {@code null} when a new conversation could not be persisted (degraded).
 */
public record ConversationOutcome(
        AgentResult result,
        String conversationId,
        MemoryStatus memoryStatus) {
}
