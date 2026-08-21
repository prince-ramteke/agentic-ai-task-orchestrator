package com.prince.agentic.ai;

import com.prince.agentic.ai.dto.AiClassificationResponse;
import com.prince.agentic.ai.dto.AiClassifyRequest;
import com.prince.agentic.ai.dto.AiGenerateRequest;
import com.prince.agentic.ai.dto.AiGenerateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal M4 demonstration of the LLM infrastructure layer. Thin: it validates input and delegates
 * to {@link AiService}. Authenticated (deny-by-default). This is <b>not</b> the agent — there is no
 * tool use, planning, or autonomy here; the agent arrives in M6 above this layer.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI", description = "LLM demonstration endpoints (M4 — no agent/tools yet)")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate a plain-text completion for a prompt")
    public AiGenerateResponse generate(@Valid @RequestBody AiGenerateRequest request) {
        return aiService.generateText(request.prompt());
    }

    @PostMapping("/classify")
    @Operation(summary = "Classify free text into a typed, validated result")
    public AiClassificationResponse classify(@Valid @RequestBody AiClassifyRequest request) {
        return aiService.classify(request.text());
    }
}
