package com.prince.agentic.memory;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Env-tunable bounds for Redis conversation memory (spec §1). Two independent boundaries:
 * <ul>
 *   <li><b>Storage</b> ({@code maxMessages}/{@code maxChars}) — what Redis keeps per conversation.</li>
 *   <li><b>Context</b> ({@code contextMaxMessages}/{@code contextMaxChars}) — the smaller slice
 *       actually rendered into the LLM prompt. The full history is never sent to the model.</li>
 * </ul>
 *
 * <p>Bound via {@code agent.memory.*}; overridable via {@code AGENT_MEMORY_TTL_SECONDS},
 * {@code AGENT_MEMORY_MAX_MESSAGES}, {@code AGENT_MEMORY_MAX_CHARS},
 * {@code AGENT_MEMORY_CONTEXT_MAX_MESSAGES}, {@code AGENT_MEMORY_CONTEXT_MAX_CHARS}. Zero/unset
 * values fall back to the defaults below so a minimal environment still binds cleanly.
 */
@Validated
@ConfigurationProperties("agent.memory")
public record MemoryProperties(
        @Min(1) int ttlSeconds,
        @Min(1) int maxMessages,
        @Min(1) int maxChars,
        @Min(1) int contextMaxMessages,
        @Min(1) int contextMaxChars) {

    public MemoryProperties {
        if (ttlSeconds == 0) ttlSeconds = 86_400;     // sliding, 24h
        if (maxMessages == 0) maxMessages = 50;
        if (maxChars == 0) maxChars = 12_000;
        if (contextMaxMessages == 0) contextMaxMessages = 12;
        if (contextMaxChars == 0) contextMaxChars = 6_000;
    }
}
