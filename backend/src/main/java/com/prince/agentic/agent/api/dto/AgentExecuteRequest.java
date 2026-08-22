package com.prince.agentic.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Agent request. Carries the message and an OPTIONAL {@code conversationId} to continue a prior
 * conversation (M7). Never userId/role/ownerId — identity comes only from the authenticated principal
 * (spec §17, §28). An absent/blank {@code conversationId} starts a new conversation; when present it
 * must be a UUID (a non-UUID is rejected with 400 before any lookup).
 */
public record AgentExecuteRequest(
        @NotBlank @Size(max = 4000) String message,
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "conversationId must be a UUID")
        String conversationId) {
}
