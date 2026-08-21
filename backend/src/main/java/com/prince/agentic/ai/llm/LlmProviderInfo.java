package com.prince.agentic.ai.llm;

/**
 * Provider + model identity, used for response metadata and metadata-only logging.
 *
 * <p>Deliberately a plain record owned by this project — never a Spring AI / vendor type. It is
 * one of the things that keeps the {@link LlmClient} abstraction free of provider leakage.
 */
public record LlmProviderInfo(String provider, String model) {}
