package com.prince.agentic.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The classification the <b>model</b> is asked to produce — the structured-output target type.
 *
 * <p>The provider's structured-output converter derives its JSON schema from exactly these fields
 * (so the model is never asked for server-side metadata). Treated as untrusted: {@code AiService}
 * validates it with Bean Validation before use.
 */
public record AiClassificationResult(

        @NotNull
        ClassificationCategory category,

        @NotNull
        ClassificationPriority priority,

        @NotBlank
        @Size(max = 500)
        String summary) {}
