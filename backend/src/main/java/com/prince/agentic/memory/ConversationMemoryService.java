package com.prince.agentic.memory;

import com.prince.agentic.security.AuthenticatedUser;

import java.util.List;

/**
 * The abstraction the agent layer depends on for short-term conversation memory (spec §2). Keeps the
 * M6 orchestrator Redis-free: it receives a rendered history string, never a Redis client.
 *
 * <p>Every operation is authorized against the authenticated principal server-side; a
 * {@code conversationId} is never an authorization claim. Implementations translate infrastructure
 * failures into {@link com.prince.agentic.memory.exception.MemoryUnavailableException} and
 * missing/foreign/expired conversations into
 * {@link com.prince.agentic.memory.exception.ConversationNotFoundException} (a masked 404).
 */
public interface ConversationMemoryService {

    /**
     * Load an existing conversation (ownership-checked) or, when {@code conversationId} is
     * {@code null}/blank, mint a new empty one. Minting performs no Redis read.
     *
     * @throws com.prince.agentic.memory.exception.ConversationNotFoundException missing/expired/foreign id
     * @throws com.prince.agentic.memory.exception.MemoryUnavailableException Redis unreachable while loading
     */
    ConversationMemory startOrLoad(AuthenticatedUser principal, String conversationId);

    /**
     * Append messages, trim to the storage bounds, and persist with a refreshed (sliding) TTL.
     * Last-write-wins from the supplied snapshot.
     *
     * @return the persisted (trimmed) memory
     * @throws com.prince.agentic.memory.exception.MemoryUnavailableException Redis unreachable while writing
     */
    ConversationMemory append(AuthenticatedUser principal, ConversationMemory memory,
                              List<MemoryMessage> newMessages);

    /**
     * Delete a conversation the principal owns.
     *
     * @throws com.prince.agentic.memory.exception.ConversationNotFoundException no such conversation for this user
     * @throws com.prince.agentic.memory.exception.MemoryUnavailableException Redis unreachable
     */
    void delete(AuthenticatedUser principal, String conversationId);

    /** The bounded, LLM-facing history string for this memory (context bounds applied). */
    String renderContext(ConversationMemory memory);
}
