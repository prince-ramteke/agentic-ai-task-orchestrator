package com.prince.agentic.memory;

/**
 * The roles persisted in conversation memory (spec §1). {@code SYSTEM} is deliberately absent:
 * the system prompt is code/config, regenerated fresh each request, and must never live in memory
 * where user-supplied text could impersonate it (see docs/SECURITY.md memory-poisoning).
 */
public enum MemoryRole {
    USER,
    ASSISTANT,
    TOOL
}
