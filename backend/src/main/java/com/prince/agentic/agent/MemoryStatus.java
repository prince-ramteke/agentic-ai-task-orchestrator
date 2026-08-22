package com.prince.agentic.agent;

/**
 * Whether conversation memory participated in this turn (spec §5). {@code ACTIVE}: the turn was
 * loaded/created and persisted. {@code UNAVAILABLE}: Redis was unreachable, so the turn ran stateless
 * (new conversation) or could not be persisted (existing conversation, best-effort append failed).
 */
public enum MemoryStatus {
    ACTIVE,
    UNAVAILABLE
}
