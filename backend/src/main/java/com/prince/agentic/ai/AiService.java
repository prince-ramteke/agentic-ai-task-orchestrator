package com.prince.agentic.ai;

import com.prince.agentic.ai.dto.AiClassificationResponse;
import com.prince.agentic.ai.dto.AiClassificationResult;
import com.prince.agentic.ai.dto.AiGenerateResponse;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.LlmProviderInfo;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import com.prince.agentic.ai.prompt.PromptService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Application-level AI service: the stable boundary future features build on.
 *
 * <p>It orchestrates prompt construction, the {@link LlmClient} call, and validation of structured
 * output, and translates outcomes into application DTOs. It deliberately knows <b>nothing</b> about
 * the database, repositories, tools, or authorization — the AI layer stays independent of the
 * Task/Customer domains (enforced by {@code ArchitectureBoundaryTest}).
 *
 * <p>Model output is untrusted: structured results are re-validated with Bean Validation after they
 * parse, with a single bounded "repair" re-ask before failing with {@link LlmInvalidOutputException}.
 * Logging is metadata-only (never the prompt or the completion).
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final LlmClient llm;
    private final PromptService prompts;
    private final Validator validator;
    private final MeterRegistry meters;

    public AiService(LlmClient llm, PromptService prompts, Validator validator, MeterRegistry meters) {
        this.llm = llm;
        this.prompts = prompts;
        this.validator = validator;
        this.meters = meters;
    }

    /** Free-form text generation. */
    public AiGenerateResponse generateText(String prompt) {
        Timer.Sample sample = Timer.start(meters);
        String outcome = "success";
        try {
            String rendered = prompts.renderGenerate(prompt);
            String content = llm.generate(rendered);
            LlmProviderInfo info = llm.info();
            return new AiGenerateResponse(content, info.model(), info.provider());
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            record(sample, "generate", outcome);
        }
    }

    /** Structured classification with untrusted-output validation and one bounded repair. */
    public AiClassificationResponse classify(String text) {
        Timer.Sample sample = Timer.start(meters);
        String outcome = "success";
        try {
            String prompt = prompts.renderClassify(text);

            AiClassificationResult result = attempt(prompt);
            if (!valid(result)) {
                log.warn("ai.classify invalid model output; attempting one repair");
                String repairPrompt = prompt
                        + "\n\nYour previous answer was invalid. Return only allowed values for every field.";
                result = attempt(repairPrompt);
                if (!valid(result)) {
                    outcome = "invalid_output";
                    throw new LlmInvalidOutputException("Model output failed validation after one repair attempt.");
                }
            }
            LlmProviderInfo info = llm.info();
            return AiClassificationResponse.of(result, info.model(), info.provider());
        } catch (LlmInvalidOutputException e) {
            outcome = "invalid_output";
            throw e;
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            record(sample, "classify", outcome);
        }
    }

    /**
     * One structured attempt. A parse/deserialization failure the provider reports as
     * {@link LlmInvalidOutputException} is normalized to a {@code null} result, so the single caller
     * repair path handles a <em>thrown</em> invalid output and a <em>returned-but-invalid</em> object
     * uniformly (and stays bounded to two model calls). Provider/timeout/unavailable errors are not
     * caught here — they propagate immediately without a wasteful retry.
     */
    private AiClassificationResult attempt(String prompt) {
        try {
            return llm.generateStructured(prompt, AiClassificationResult.class);
        } catch (LlmInvalidOutputException parseFailure) {
            return null;
        }
    }

    private boolean valid(AiClassificationResult result) {
        if (result == null) {
            return false;
        }
        Set<ConstraintViolation<AiClassificationResult>> violations = validator.validate(result);
        return violations.isEmpty();
    }

    private void record(Timer.Sample sample, String op, String outcome) {
        LlmProviderInfo info = safeInfo();
        sample.stop(Timer.builder("llm.request.duration")
                .tag("op", op)
                .tag("provider", info.provider())
                .tag("model", info.model())
                .tag("outcome", outcome)
                .register(meters));
        meters.counter("llm.request.result",
                "op", op, "provider", info.provider(), "model", info.model(), "outcome", outcome)
                .increment();
        log.info("ai.{} provider={} model={} outcome={}", op, info.provider(), info.model(), outcome);
    }

    /** Never let metric/label resolution throw over a real result or error. */
    private LlmProviderInfo safeInfo() {
        try {
            return llm.info();
        } catch (RuntimeException e) {
            return new LlmProviderInfo("unknown", "unknown");
        }
    }
}
