package com.prince.agentic.memory;

import java.time.Instant;
import java.util.List;

/**
 * The full, application-owned representation of one conversation's short-term memory (spec §2).
 * Serialized to Redis as a single JSON blob — never Java native serialization, never class-name
 * polymorphic storage. {@code schemaVersion} is reserved for future migrations.
 *
 * <p>{@code ownerUserId} is the authoritative owner: it is asserted equal to the authenticated
 * principal on every load (defense-in-depth on top of the userId-scoped Redis key), so a guessed
 * or manipulated {@code conversationId} can never reach another user's memory.
 */
public record ConversationMemory(
        String conversationId,
        long ownerUserId,
        Instant createdAt,
        Instant lastActivityAt,
        int schemaVersion,
        List<MemoryMessage> messages) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ConversationMemory {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /** A fresh, empty conversation owned by {@code ownerUserId}. */
    public static ConversationMemory create(String conversationId, long ownerUserId, Instant now) {
        return new ConversationMemory(conversationId, ownerUserId, now, now,
                CURRENT_SCHEMA_VERSION, List.of());
    }

    /** Next sequence number for an appended message. */
    public int nextSequence() {
        return messages.isEmpty() ? 0 : messages.get(messages.size() - 1).sequence() + 1;
    }
}
