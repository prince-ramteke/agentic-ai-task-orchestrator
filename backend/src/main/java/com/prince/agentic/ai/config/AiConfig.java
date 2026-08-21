package com.prince.agentic.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.time.Duration;

/**
 * Wiring for the LLM layer. Coverage-excluded infrastructure (like {@code config/**}).
 *
 * <p>Builds a {@link ChatClient} over the auto-configured {@link OllamaChatModel} (only when the
 * Ollama provider is active) and applies explicit connect + read timeouts to the HTTP client Spring
 * AI uses, so an LLM call fails fast within a bound rather than hanging the request thread.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AiConfig {

    /** ChatClient over the auto-configured Ollama model. Only created when the Ollama provider is active. */
    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
    ChatClient ollamaChatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Apply the configured timeout to the RestClient Spring AI's Ollama client is built from.
     * Boot applies every {@link RestClientCustomizer} to the {@code RestClient.Builder} the Ollama
     * auto-config consumes, so this bounds both connect and read waits.
     */
    @Bean
    RestClientCustomizer llmRestClientCustomizer(LlmProperties props) {
        Duration timeout = Duration.ofSeconds(props.getRequestTimeoutSeconds());
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder -> builder.requestFactory(factory);
    }
}
