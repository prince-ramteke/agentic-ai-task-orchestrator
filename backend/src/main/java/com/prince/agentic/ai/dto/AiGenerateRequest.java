package com.prince.agentic.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/ai/generate}. The prompt is required and bounded — no
 * unlimited arbitrary text is accepted.
 */
public record AiGenerateRequest(

        @NotBlank
        @Size(min = 1, max = 4000)
        String prompt) {}
