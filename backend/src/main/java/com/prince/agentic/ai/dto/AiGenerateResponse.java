package com.prince.agentic.ai.dto;

/**
 * Response for {@code POST /api/v1/ai/generate}. Application-owned shape — never a raw Spring AI
 * object. {@code model}/{@code provider} are server-supplied metadata, not model output.
 */
public record AiGenerateResponse(String content, String model, String provider) {}
