package com.prince.agentic.guardrail;

/**
 * Backend-controlled correlation passed into a guardrail evaluation (spec §4). Carries only
 * infrastructure identifiers for logging/metrics — never identity, user text, or model output, so
 * that no untrusted content can become a policy input.
 *
 * @param executionId the agent execution id this decision belongs to
 * @param requestId   the originating request correlation id
 */
public record GuardrailContext(String executionId, String requestId) {

    /** A context with no correlation (used by unit tests and stateless callers). */
    public static GuardrailContext none() {
        return new GuardrailContext(null, null);
    }
}
