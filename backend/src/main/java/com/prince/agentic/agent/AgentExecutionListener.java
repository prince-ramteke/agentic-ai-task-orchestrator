package com.prince.agentic.agent;

/**
 * The audit seam (spec §8, locked decision 2). The orchestrator and confirm service emit
 * backend-observed execution facts to this sink at lifecycle points; a persistence implementation
 * (M9 audit module) records them. The interface is deliberately <b>repository-free</b> and uses only
 * agent-native value types, so the agent core never depends on JPA, a repository, or the audit module
 * — the dependency runs one way (audit → agent). The default {@link NoOpAgentExecutionListener} makes
 * audit fully optional: the agent runs identically with or without a recorder.
 *
 * <p>Implementations MUST be non-throwing from the caller's perspective (best-effort): a recorder
 * failure must never propagate into the agent/domain execution path (locked decision 1).
 */
public interface AgentExecutionListener {

    void onExecutionStarted(AuditExecutionStart event);

    void onStep(AuditStepEvent event);

    void onToolExecution(AuditToolEvent event);

    void onExecutionCompleted(AuditExecutionEnd event);

    /**
     * A confirmed side-effecting action executed (M8 confirm path, spec §8): appends a
     * {@code CONFIRMATION_APPROVED} step + its tool execution to the originating run and promotes that
     * run's {@code PENDING_CONFIRMATION} to {@code COMPLETED}/{@code FAILED}. Emitted from a separate
     * HTTP request than the run itself — no LLM loop is resumed.
     */
    void onConfirmationExecuted(AuditConfirmationExecuted event);
}
