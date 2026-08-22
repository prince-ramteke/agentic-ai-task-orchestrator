package com.prince.agentic.audit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe view of one audited step (spec §22): typed facts only, no free text or reasoning. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentStepResponse(
        int sequence,
        String type,
        String status,
        String toolName,
        String detailCode,
        Long durationMs) {
}
