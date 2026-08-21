package com.prince.agentic.ai.llm.exception;

import com.prince.agentic.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the HTTP status + stable machine code of each LLM exception, and that they all extend
 * {@link ApiException} so the existing global handler renders them without new plumbing.
 */
class LlmExceptionTest {

    @Test
    void unavailable_maps_to_503_and_code() {
        LlmException ex = new LlmUnavailableException("ollama down");
        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getCode()).isEqualTo("LLM_UNAVAILABLE");
    }

    @Test
    void timeout_maps_to_504_and_code() {
        LlmException ex = new LlmTimeoutException("slow");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(ex.getCode()).isEqualTo("LLM_TIMEOUT");
    }

    @Test
    void provider_maps_to_502_and_code_and_keeps_cause() {
        Throwable cause = new IllegalStateException("root");
        LlmProviderException ex = new LlmProviderException("boom", cause);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ex.getCode()).isEqualTo("LLM_PROVIDER_ERROR");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void provider_tolerates_null_cause() {
        LlmProviderException ex = new LlmProviderException("boom", null);
        assertThat(ex.getCause()).isNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void invalidOutput_maps_to_422_and_code() {
        LlmException ex = new LlmInvalidOutputException("bad json");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getCode()).isEqualTo("LLM_INVALID_OUTPUT");
    }
}
