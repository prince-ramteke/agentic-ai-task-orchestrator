package com.prince.agentic.agent;

import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

/** The default sink must accept every event and do nothing — audit is fully optional. */
class NoOpAgentExecutionListenerTest {

    private final AgentExecutionListener listener = new NoOpAgentExecutionListener();
    private static final Instant T = Instant.parse("2026-08-22T12:00:00Z");

    @Test
    void allCallbacks_areNoOps() {
        assertThatCode(() -> {
            listener.onExecutionStarted(new AuditExecutionStart("x", 1L, "c", "r", T));
            listener.onStep(new AuditStepEvent("x", 0, AgentStepKind.FINAL, AgentStepOutcome.OK,
                    null, null, T, T, 1L));
            listener.onToolExecution(new AuditToolEvent("te", "x", 0, "t", ToolRiskLevel.READ_ONLY,
                    AgentToolOutcome.SUCCESS, null, null, null, null, T, T, 1L));
            listener.onExecutionCompleted(new AuditExecutionEnd("x", AgentStatus.COMPLETED, null,
                    null, 1, 0, T, 1L));
        }).doesNotThrowAnyException();
    }
}
