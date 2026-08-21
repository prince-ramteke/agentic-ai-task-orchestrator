package com.prince.agentic.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/ai/classify}. Bounded free text, required. */
public record AiClassifyRequest(

        @NotBlank
        @Size(min = 1, max = 4000)
        String text) {}
