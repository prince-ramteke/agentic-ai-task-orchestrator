package com.prince.agentic.ai.dto;

/**
 * Response for {@code POST /api/v1/ai/classify}: the validated model result plus server-supplied
 * provider metadata. Assembled by {@code AiService} — the model never fills {@code model}/{@code provider}.
 */
public record AiClassificationResponse(
        ClassificationCategory category,
        ClassificationPriority priority,
        String summary,
        String model,
        String provider) {

    /** Assemble the API response from a validated result and the active provider identity. */
    public static AiClassificationResponse of(AiClassificationResult r, String model, String provider) {
        return new AiClassificationResponse(r.category(), r.priority(), r.summary(), model, provider);
    }
}
