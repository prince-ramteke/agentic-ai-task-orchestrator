package com.prince.agentic.agent;

/**
 * Terminal agent run statuses (spec §15/§22). M8 adds two guardrail terminals:
 * {@code PENDING_CONFIRMATION} (a side-effecting/high-risk action halted awaiting explicit
 * confirmation) and {@code BLOCKED} (a policy denied the action, or a rate limit tripped).
 */
public enum AgentStatus {
    COMPLETED, FAILED, TIMED_OUT, CANCELLED, LIMIT_REACHED, LOOP_DETECTED,
    PENDING_CONFIRMATION, BLOCKED
}
