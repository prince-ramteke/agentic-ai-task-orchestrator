package com.prince.agentic.ai.prompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Renders versioned prompt templates from {@code resources/prompts/}.
 *
 * <p>Deliberately a plain, provider-agnostic text renderer — it imports no Spring AI type, so the
 * abstraction boundary (only {@code ai.llm.ollama} touches Spring AI) holds. Untrusted user input
 * is substituted only into the delimited {@code {input}} slot; the instruction text is fixed and
 * never built from user data. The structured-output format instruction is added by the provider's
 * converter inside {@code OllamaLlmClient}, not here.
 */
@Service
public class PromptService {

    private static final String INPUT_TOKEN = "{input}";

    private final String generateTemplate;
    private final String classifyTemplate;

    public PromptService(
            @Value("classpath:prompts/generate.st") Resource generateTemplate,
            @Value("classpath:prompts/classify.st") Resource classifyTemplate) {
        this.generateTemplate = read(generateTemplate);
        this.classifyTemplate = read(classifyTemplate);
    }

    /** Render the text-generation prompt with the untrusted input delimited. */
    public String renderGenerate(String input) {
        return generateTemplate.replace(INPUT_TOKEN, safe(input));
    }

    /** Render the classification prompt with the untrusted input delimited. */
    public String renderClassify(String input) {
        return classifyTemplate.replace(INPUT_TOKEN, safe(input));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String read(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load prompt template: " + resource.getDescription(), e);
        }
    }
}
