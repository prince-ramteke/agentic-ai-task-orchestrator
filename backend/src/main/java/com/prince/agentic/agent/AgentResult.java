package com.prince.agentic.agent;

/** Structured outcome of one agent run. failureCode is null for COMPLETED (spec §15). */
public record AgentResult(
        String executionId,
        AgentStatus status,
        String finalResponse,
        int iterations,
        int toolCalls,
        long durationMs,
        String failureCode) {
}
