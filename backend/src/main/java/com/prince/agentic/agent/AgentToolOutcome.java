package com.prince.agentic.agent;

/**
 * Outcome of a tool execution as observed by the agent (spec §7) — distinct concepts, never conflated:
 * {@code SUCCESS} (ran, ok), {@code FAILED} (ran, errored), {@code DENIED} (guardrail policy denial, no
 * execution), {@code NOT_EXECUTED} (unknown tool / never reached), {@code CONFIRMATION_REQUIRED}
 * (halted awaiting confirm), {@code TIMEOUT} (pre-execution budget/deadline).
 */
public enum AgentToolOutcome {
    SUCCESS,
    FAILED,
    DENIED,
    NOT_EXECUTED,
    CONFIRMATION_REQUIRED,
    TIMEOUT
}
