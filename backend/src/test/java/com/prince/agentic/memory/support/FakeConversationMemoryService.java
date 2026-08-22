package com.prince.agentic.memory.support;

import com.prince.agentic.memory.ConversationMemory;
import com.prince.agentic.memory.ConversationMemoryService;
import com.prince.agentic.memory.MemoryBounds;
import com.prince.agentic.memory.MemoryMessage;
import com.prince.agentic.memory.exception.ConversationNotFoundException;
import com.prince.agentic.memory.exception.MemoryUnavailableException;
import com.prince.agentic.security.AuthenticatedUser;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic in-memory {@link ConversationMemoryService} double for agent/service unit tests
 * (no Redis, no network). Mirrors the real ownership/404 semantics and bounded trimming, and can
 * simulate a Redis outage via {@link #setAvailable(boolean)}.
 */
public class FakeConversationMemoryService implements ConversationMemoryService {

    private final Map<String, ConversationMemory> store = new ConcurrentHashMap<>();
    private final Clock clock;
    private boolean available = true;

    // Bounds mirror the production defaults; small enough to exercise trimming in tests.
    private int maxMessages = 50;
    private int maxChars = 12_000;
    private int contextMaxMessages = 12;
    private int contextMaxChars = 6_000;

    public FakeConversationMemoryService(Clock clock) {
        this.clock = clock;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setBounds(int maxMessages, int maxChars, int contextMaxMessages, int contextMaxChars) {
        this.maxMessages = maxMessages;
        this.maxChars = maxChars;
        this.contextMaxMessages = contextMaxMessages;
        this.contextMaxChars = contextMaxChars;
    }

    @Override
    public ConversationMemory startOrLoad(AuthenticatedUser principal, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return ConversationMemory.create(UUID.randomUUID().toString(),
                    principal.userId(), clock.instant());
        }
        requireAvailable();
        ConversationMemory memory = store.get(key(principal.userId(), conversationId));
        if (memory == null || memory.ownerUserId() != principal.userId()) {
            throw new ConversationNotFoundException();
        }
        return memory;
    }

    @Override
    public ConversationMemory append(AuthenticatedUser principal, ConversationMemory memory,
                                     List<MemoryMessage> newMessages) {
        if (memory.ownerUserId() != principal.userId()) {
            throw new ConversationNotFoundException();
        }
        requireAvailable();
        List<MemoryMessage> combined = new ArrayList<>(memory.messages());
        combined.addAll(newMessages);
        List<MemoryMessage> trimmed = MemoryBounds.trimForStorage(combined, maxMessages, maxChars);
        ConversationMemory updated = new ConversationMemory(memory.conversationId(),
                memory.ownerUserId(), memory.createdAt(), clock.instant(),
                ConversationMemory.CURRENT_SCHEMA_VERSION, trimmed);
        store.put(key(principal.userId(), updated.conversationId()), updated);
        return updated;
    }

    @Override
    public void delete(AuthenticatedUser principal, String conversationId) {
        requireAvailable();
        if (store.remove(key(principal.userId(), conversationId)) == null) {
            throw new ConversationNotFoundException();
        }
    }

    @Override
    public String renderContext(ConversationMemory memory) {
        return MemoryBounds.renderContext(memory.messages(), contextMaxMessages, contextMaxChars);
    }

    private void requireAvailable() {
        if (!available) {
            throw new MemoryUnavailableException(new IllegalStateException("fake redis down"));
        }
    }

    private String key(long userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
