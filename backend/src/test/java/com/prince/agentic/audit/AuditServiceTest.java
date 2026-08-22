package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentStatus;
import com.prince.agentic.agent.AgentStepKind;
import com.prince.agentic.agent.AgentStepOutcome;
import com.prince.agentic.agent.AgentToolOutcome;
import com.prince.agentic.agent.AuditExecutionEnd;
import com.prince.agentic.agent.AuditExecutionStart;
import com.prince.agentic.agent.AuditStepEvent;
import com.prince.agentic.agent.AuditToolEvent;
import com.prince.agentic.tool.ToolRiskLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Best-effort semantics: writes are metered; a writer failure is swallowed (never rethrown). */
class AuditServiceTest {

    private final AuditWriter writer = mock(AuditWriter.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AuditService service = new AuditService(writer, meters);

    private static final Instant T = Instant.parse("2026-08-22T12:00:00Z");

    private double count(String name) {
        return meters.find(name).counter() == null ? 0.0 : meters.find(name).counter().count();
    }

    @Test
    void onExecutionStarted_success_metersSuccessAndCreated() {
        service.onExecutionStarted(new AuditExecutionStart("x", 1L, "c", "r", T));
        verify(writer).createExecution(any());
        assertThat(count("audit.write.success")).isEqualTo(1.0);
        assertThat(count("audit.execution.created")).isEqualTo(1.0);
    }

    @Test
    void writerFailure_isSwallowed_andMetersFailure() {
        doThrow(new RuntimeException("db down")).when(writer).createExecution(any());
        assertThatCode(() -> service.onExecutionStarted(new AuditExecutionStart("x", 1L, "c", "r", T)))
                .doesNotThrowAnyException(); // best-effort: never propagates into the agent path
        assertThat(count("audit.write.failure")).isEqualTo(1.0);
        assertThat(count("audit.write.success")).isZero();
    }

    @Test
    void onStep_meters() {
        service.onStep(new AuditStepEvent("x", 0, AgentStepKind.GUARDRAIL, AgentStepOutcome.REQUIRED,
                "task.create", "CONFIRMATION_REQUIRED", T, T, 1L));
        verify(writer).addStep(any());
        assertThat(count("audit.step.created")).isEqualTo(1.0);
    }

    @Test
    void onToolExecution_meters() {
        service.onToolExecution(new AuditToolEvent("te", "x", 0, "task.search", ToolRiskLevel.READ_ONLY,
                AgentToolOutcome.SUCCESS, null, null, "hash", "ok", T, T, 2L));
        verify(writer).addToolExecution(any());
        assertThat(count("audit.tool_execution.created")).isEqualTo(1.0);
    }

    @Test
    void onExecutionCompleted_metersSuccess() {
        service.onExecutionCompleted(new AuditExecutionEnd("x", AgentStatus.COMPLETED, null, "done",
                2, 1, T, 5L));
        verify(writer).completeExecution(any());
        assertThat(count("audit.write.success")).isEqualTo(1.0);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
