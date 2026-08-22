package com.prince.agentic.guardrail;

/**
 * The three — and only three — guardrail outcomes (spec §3). The engine never invents others.
 *
 * <ul>
 *   <li>{@code ALLOW} — proceed to execution (still through {@code ToolExecutor}'s own gates).</li>
 *   <li>{@code DENY} — refuse; no effect happens; surfaced as a blocked observation/failure code.</li>
 *   <li>{@code REQUIRE_CONFIRMATION} — halt and require explicit human confirmation before execution.</li>
 * </ul>
 */
public enum GuardrailOutcome {
    ALLOW,
    DENY,
    REQUIRE_CONFIRMATION
}
