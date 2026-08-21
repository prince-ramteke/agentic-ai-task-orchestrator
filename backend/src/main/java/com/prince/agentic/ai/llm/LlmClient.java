package com.prince.agentic.ai.llm;

/**
 * The single, provider-agnostic path to the language model.
 *
 * <p>The rest of the application depends on this interface, never on a vendor SDK
 * ({@code OllamaChatModel}/{@code ChatClient}). Providers (Ollama today; potentially others later)
 * are swappable without touching callers. This is the boundary the future agent (M6) builds on.
 *
 * <p>Implementations translate provider/transport failures into the
 * {@link com.prince.agentic.ai.llm.exception.LlmException} hierarchy so callers get a uniform,
 * envelope-mappable error model.
 */
public interface LlmClient {

    /**
     * Free-form text generation.
     *
     * @param prompt the fully-rendered prompt (callers build it via the prompt layer)
     * @return the model's text completion
     * @throws com.prince.agentic.ai.llm.exception.LlmException on any provider/transport failure
     */
    String generate(String prompt);

    /**
     * Structured generation into a typed object using the provider's structured-output support.
     *
     * <p>The returned object has been parsed but is <b>not</b> yet trusted — the caller
     * ({@code AiService}) validates it with Bean Validation before use.
     *
     * @param prompt the fully-rendered prompt
     * @param type   the target type to parse into
     * @return an instance of {@code type} populated from the model output
     * @throws com.prince.agentic.ai.llm.exception.LlmException on any provider/transport failure
     */
    <T> T generateStructured(String prompt, Class<T> type);

    /** Provider + model identity for response metadata and logging. Never a vendor type. */
    LlmProviderInfo info();
}
