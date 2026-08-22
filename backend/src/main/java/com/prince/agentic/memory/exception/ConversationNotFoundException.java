package com.prince.agentic.memory.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A conversation does not exist, has expired, or is not owned by the authenticated user (spec §1).
 * Deliberately a single 404 for all three: never reveal that another user's conversation exists
 * (existence-masking, matching the M3/M5 ownership convention).
 */
public class ConversationNotFoundException extends ApiException {

    public ConversationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found.");
    }
}
