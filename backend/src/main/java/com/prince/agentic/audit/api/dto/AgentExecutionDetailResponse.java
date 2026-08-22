package com.prince.agentic.audit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Safe detail view of one audited execution (spec §22): the summary fields plus the ordered steps and
 * tool executions and the bounded {@code finalResponseSummary}. No internal class names, raw prompts,
 * arguments, LLM output, chain-of-thought, stack traces, or secrets are ever included.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentExecutionDetailResponse(
        String executionId,
        String status,
        String conversationId,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        int iterations,
        int toolCalls,
        String failureCode,
        String finalResponseSummary,
        List<AgentStepResponse> steps,
        List<ToolExecutionResponse> toolExecutions) {
}
