package com.prince.agentic.audit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Safe view of one audited tool execution (spec §22). Never exposes raw arguments/results — only the
 * bounded {@code resultSummary}, {@code argumentsHash}, and outcome metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolExecutionResponse(
        String toolName,
        String riskLevel,
        String outcome,
        String errorCode,
        String confirmationId,
        String argumentsHash,
        String resultSummary,
        Long durationMs) {
}
