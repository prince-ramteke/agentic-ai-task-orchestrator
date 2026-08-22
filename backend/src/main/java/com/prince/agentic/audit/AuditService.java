package com.prince.agentic.audit;

import com.prince.agentic.agent.AgentExecutionListener;
import com.prince.agentic.agent.AuditConfirmationExecuted;
import com.prince.agentic.agent.AuditExecutionEnd;
import com.prince.agentic.agent.AuditExecutionStart;
import com.prince.agentic.agent.AuditStepEvent;
import com.prince.agentic.agent.AuditToolEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Best-effort durable recorder for agent execution facts (spec §9, locked decision 1). Implements the
 * agent-side {@link AgentExecutionListener} seam and delegates each write to the transactional
 * {@link AuditWriter} ({@code REQUIRES_NEW}). Every write is wrapped so a persistence failure is
 * <b>swallowed</b>: it is logged WARN and counted as {@code audit.write.failure}, and <b>never</b>
 * rethrown into the agent/domain execution path. A business action can therefore succeed while its
 * audit row is temporarily missing — that gap is recorded (metric), not hidden.
 *
 * <p>{@code @Primary} so this real recorder wins injection over {@code NoOpAgentExecutionListener}
 * whenever the audit module is present.
 */
@Service
@Primary
public class AuditService implements AgentExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditWriter writer;
    private final MeterRegistry meters;

    public AuditService(AuditWriter writer, MeterRegistry meters) {
        this.writer = writer;
        this.meters = meters;
    }

    @Override
    public void onExecutionStarted(AuditExecutionStart event) {
        write("execution", () -> {
            writer.createExecution(event);
            meters.counter("audit.execution.created").increment();
        });
    }

    @Override
    public void onStep(AuditStepEvent event) {
        write("step", () -> {
            writer.addStep(event);
            meters.counter("audit.step.created", "stepType", event.kind().name()).increment();
        });
    }

    @Override
    public void onToolExecution(AuditToolEvent event) {
        write("tool_execution", () -> {
            writer.addToolExecution(event);
            meters.counter("audit.tool_execution.created", "outcome", event.outcome().name()).increment();
        });
    }

    @Override
    public void onExecutionCompleted(AuditExecutionEnd event) {
        write("execution_complete", () -> writer.completeExecution(event));
    }

    @Override
    public void onConfirmationExecuted(AuditConfirmationExecuted event) {
        write("confirmation_executed", () -> {
            writer.recordConfirmationExecution(event);
            meters.counter("audit.step.created", "stepType", "CONFIRMATION_APPROVED").increment();
        });
    }

    /** Run one audit write; success/failure are metered, and failures never escape. */
    private void write(String kind, Runnable action) {
        try {
            action.run();
            meters.counter("audit.write.success").increment();
        } catch (RuntimeException e) {
            // Best-effort: audit must never block or roll back the agent/domain path.
            meters.counter("audit.write.failure", "kind", kind).increment();
            log.warn("audit.write.failure kind={} : {}", kind, e.getClass().getSimpleName());
        }
    }
}
