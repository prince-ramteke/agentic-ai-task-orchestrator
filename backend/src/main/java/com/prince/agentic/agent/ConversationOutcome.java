package com.prince.agentic.agent;

import com.prince.agentic.guardrail.confirmation.PendingConfirmation;

/**
 * The result of one conversational agent turn: the bounded run outcome plus the memory metadata the
 * API surfaces. {@code conversationId} is the server-minted id to continue the conversation, or
 * {@code null} when a new conversation could not be persisted (degraded).
 *
 * <p>{@code pendingConfirmation} (M8) is non-null only when the run halted at
 * {@code PENDING_CONFIRMATION}: the safe, client-facing view of the stored confirmation the user must
 * approve before the side-effecting action can run.
 */
public record ConversationOutcome(
        AgentResult result,
        String conversationId,
        MemoryStatus memoryStatus,
        PendingConfirmation pendingConfirmation) {

    /** Convenience for the non-confirmation turns (pendingConfirmation = null). */
    public ConversationOutcome(AgentResult result, String conversationId, MemoryStatus memoryStatus) {
        this(result, conversationId, memoryStatus, null);
    }
}
