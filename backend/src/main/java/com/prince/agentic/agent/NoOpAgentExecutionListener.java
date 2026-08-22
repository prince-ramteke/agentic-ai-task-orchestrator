package com.prince.agentic.agent;

import org.springframework.stereotype.Component;

/**
 * The default, do-nothing audit sink (spec §8, locked decision 2). Guarantees the agent core runs
 * identically whether or not a durable recorder is present. The M9 audit module supplies a
 * {@code @Primary} persistence implementation that wins injection when present; this bean remains the
 * safe fallback and the injection target for tests that don't exercise audit.
 */
@Component
public class NoOpAgentExecutionListener implements AgentExecutionListener {

    @Override
    public void onExecutionStarted(AuditExecutionStart event) {
        // no-op
    }

    @Override
    public void onStep(AuditStepEvent event) {
        // no-op
    }

    @Override
    public void onToolExecution(AuditToolEvent event) {
        // no-op
    }

    @Override
    public void onExecutionCompleted(AuditExecutionEnd event) {
        // no-op
    }

    @Override
    public void onConfirmationExecuted(AuditConfirmationExecuted event) {
        // no-op
    }
}
