package com.prince.agentic.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Agent request. Carries ONLY the message — never userId/role/ownerId (spec 17, 28). */
public record AgentExecuteRequest(@NotBlank @Size(max = 4000) String message) {
}
