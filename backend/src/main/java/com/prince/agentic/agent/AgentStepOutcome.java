package com.prince.agentic.agent;

/**
 * Outcome status of a single audited step (spec §3.2). {@code REQUIRED} = halted awaiting confirmation;
 * {@code BLOCKED} = a guardrail denial; {@code OK}/{@code FAILED} = the rest.
 */
public enum AgentStepOutcome {
    OK,
    FAILED,
    BLOCKED,
    REQUIRED
}
