package com.prince.agentic.agent;

import com.prince.agentic.guardrail.FingerprintService;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stateless helper that builds audit event records and forwards them to the {@link AgentExecutionListener}
 * (spec §8). Centralizes event construction, duration computation, and {@code arguments_hash}
 * generation so the orchestrator/confirm service hold a single audit dependency and stay free of both
 * JPA and hashing details. It only produces events — the (best-effort, transactional) persistence lives
 * behind the listener. Sequence numbers and timings are supplied by the caller (which owns per-run state).
 */
@Component
public class AgentAuditEmitter {

    private final AgentExecutionListener listener;
    private final FingerprintService fingerprints;

    public AgentAuditEmitter(AgentExecutionListener listener, FingerprintService fingerprints) {
        this.listener = listener;
        this.fingerprints = fingerprints;
    }

    public void started(String executionUid, long ownerId, String conversationId, String requestId,
                        Instant startedAt) {
        listener.onExecutionStarted(
                new AuditExecutionStart(executionUid, ownerId, conversationId, requestId, startedAt));
    }

    public void step(String executionUid, int sequence, AgentStepKind kind, AgentStepOutcome outcome,
                     String toolName, String detailCode, Instant startedAt, Instant completedAt) {
        listener.onStep(new AuditStepEvent(executionUid, sequence, kind, outcome, toolName, detailCode,
                startedAt, completedAt, millis(startedAt, completedAt)));
    }

    /** Emit a tool-execution fact linked to its step. {@code arguments} are hashed, never stored raw. */
    public void toolExecution(String executionUid, int stepSequence, String toolName, ToolRiskLevel risk,
                              Map<String, Object> arguments, AgentToolOutcome outcome, String errorCode,
                              String confirmationId, String resultSummary, Instant startedAt,
                              Instant completedAt) {
        String argsHash = arguments == null ? null : fingerprints.argumentsHashHex(arguments);
        listener.onToolExecution(new AuditToolEvent(UUID.randomUUID().toString(), executionUid,
                stepSequence, toolName, risk, outcome, errorCode, confirmationId, argsHash, resultSummary,
                startedAt, completedAt, millis(startedAt, completedAt)));
    }

    /** Emit a confirmed-action execution fact (M8 confirm path). Arguments are hashed, never stored raw. */
    public void confirmationExecuted(String executionUid, String confirmationId, String toolName,
                                     ToolRiskLevel risk, Map<String, Object> arguments, boolean success,
                                     String errorCode, String resultSummary, Instant startedAt,
                                     Instant completedAt) {
        String argsHash = arguments == null ? null : fingerprints.argumentsHashHex(arguments);
        listener.onConfirmationExecuted(new AuditConfirmationExecuted(executionUid, confirmationId,
                toolName, risk, argsHash, success, errorCode, resultSummary, startedAt, completedAt));
    }

    public void completed(String executionUid, AgentStatus status, String failureCode,
                          String finalResponseSummary, int iterations, int toolCalls,
                          Instant completedAt, long durationMs) {
        listener.onExecutionCompleted(new AuditExecutionEnd(executionUid, status, failureCode,
                finalResponseSummary, iterations, toolCalls, completedAt, durationMs));
    }

    private static Long millis(Instant a, Instant b) {
        return (a == null || b == null) ? null : Duration.between(a, b).toMillis();
    }
}
