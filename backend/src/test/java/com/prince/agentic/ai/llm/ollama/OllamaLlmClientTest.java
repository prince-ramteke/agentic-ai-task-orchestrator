package com.prince.agentic.ai.llm.ollama;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import com.prince.agentic.ai.llm.exception.LlmProviderException;
import com.prince.agentic.ai.llm.exception.LlmTimeoutException;
import com.prince.agentic.ai.llm.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-tests the transport-failure → LlmException mapping directly (no fluent-chain mocking needed).
 * The chat/props dependencies are unused by {@code map(...)}, so nulls are fine here.
 */
class OllamaLlmClientTest {

    private final OllamaLlmClient client = new OllamaLlmClient(null, null);

    @Test
    void socket_timeout_maps_to_timeout() {
        RuntimeException wrapped = new ResourceAccessException("io", new SocketTimeoutException("read timed out"));
        assertThat(client.map(wrapped)).isInstanceOf(LlmTimeoutException.class);
    }

    @Test
    void connect_refused_maps_to_unavailable() {
        RuntimeException wrapped = new ResourceAccessException("io", new ConnectException("Connection refused"));
        assertThat(client.map(wrapped)).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void bare_resource_access_maps_to_unavailable() {
        assertThat(client.map(new ResourceAccessException("host down")))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void structured_conversion_failure_maps_to_invalid_output() {
        // Mirrors the real Spring AI BeanOutputConverter failure: a RuntimeException caused by a
        // Jackson deserialization error (e.g. an out-of-range enum value from the model).
        InvalidFormatException jackson =
                InvalidFormatException.from(null, "not an enum value", "FEATURE", Enum.class);
        RuntimeException converterFailure = new RuntimeException("conversion failed", jackson);
        assertThat(client.map(converterFailure)).isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    void generic_rest_client_error_maps_to_provider_error() {
        assertThat(client.map(new RestClientException("500 from provider")))
                .isInstanceOf(LlmProviderException.class);
    }

    @Test
    void unknown_runtime_error_maps_to_provider_error() {
        assertThat(client.map(new IllegalStateException("weird")))
                .isInstanceOf(LlmProviderException.class);
    }
}
