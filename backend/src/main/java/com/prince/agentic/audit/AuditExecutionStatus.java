package com.prince.agentic.audit;

/**
 * Durable execution status of an audited agent run (spec §5). {@code STARTED} is written at run start;
 * a run then reaches one terminal. {@code PENDING_CONFIRMATION} is a real terminal of the agent loop
 * (M8 does not auto-resume); a successful confirm later promotes it to {@code COMPLETED}/{@code FAILED}.
 * {@code RUNNING} is defined for completeness but not persisted as a distinct state (short runs).
 * Values mirror {@code com.prince.agentic.agent.AgentStatus} plus {@code STARTED}/{@code RUNNING}.
 */
public enum AuditExecutionStatus {
    STARTED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    LIMIT_REACHED,
    LOOP_DETECTED,
    PENDING_CONFIRMATION,
    BLOCKED
}
