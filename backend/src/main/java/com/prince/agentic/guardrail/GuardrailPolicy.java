package com.prince.agentic.guardrail;

/**
 * One deterministic policy (spec §4). Policies are plain Java beans — never a rules DSL. The engine
 * runs them in ascending {@link #order()} and the first non-{@code ALLOW} decision wins, so each
 * policy only needs to answer for its own concern and return {@link GuardrailDecision#allow()} to
 * defer to the next. Implementations must be pure (no side effects) and must not treat any user or
 * model text as authority.
 */
public interface GuardrailPolicy {

    GuardrailDecision evaluate(GuardrailInput input);

    /** Lower runs first. */
    int order();
}
