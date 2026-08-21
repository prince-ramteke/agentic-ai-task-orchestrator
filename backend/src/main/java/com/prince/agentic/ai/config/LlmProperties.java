package com.prince.agentic.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the application's own {@code llm.*} keys, kept intentionally separate from the
 * {@code spring.ai.*} auto-configuration keys so provider configuration is explicit and readable.
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** Active provider. Only {@code ollama} is wired in M4 ({@code openai} is future). */
    private String provider = "ollama";

    /** Connect + read timeout applied to the LLM HTTP client. */
    private int requestTimeoutSeconds = 60;

    private Ollama ollama = new Ollama();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }

    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama; }

    /** Ollama-specific settings (mirrors {@code spring.ai.ollama.*} for our own use, e.g. metadata). */
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.2";
        private double temperature = 0.2;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
    }
}
