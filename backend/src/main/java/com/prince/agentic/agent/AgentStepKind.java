package com.prince.agentic.agent;

/**
 * A meaningful, auditable agent transition (spec §6). Owned by the agent domain (the producer of the
 * facts) so the audit module depends on the agent, never the reverse. Stable, small set — no new kinds
 * without justification. Guardrail and confirmation outcomes are typed here, not a generic events bag.
 */
public enum AgentStepKind {
    LLM_DECISION,
    GUARDRAIL,
    TOOL_CALL,
    CONFIRMATION_REQUIRED,
    CONFIRMATION_APPROVED,
    CONFIRMATION_REJECTED,
    FINAL,
    FAILURE
}
