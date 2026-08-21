package com.prince.agentic.ai.support;

import com.prince.agentic.ai.dto.AiClassificationResult;
import com.prince.agentic.ai.dto.ClassificationCategory;
import com.prince.agentic.ai.dto.ClassificationPriority;
import com.prince.agentic.ai.llm.exception.LlmProviderException;
import com.prince.agentic.ai.llm.exception.LlmTimeoutException;
import com.prince.agentic.ai.llm.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the FakeLlmClient's deterministic modes behave as tests rely on. */
class FakeLlmClientTest {

    @Test
    void generate_returns_canned_text_in_valid_mode() {
        FakeLlmClient fake = new FakeLlmClient();
        assertThat(fake.generate("hello")).isNotBlank();
        assertThat(fake.info().provider()).isEqualTo("fake");
        assertThat(fake.info().model()).isEqualTo("fake-model");
    }

    @Test
    void structured_returns_configured_object() {
        FakeLlmClient fake = new FakeLlmClient();
        fake.setStructured(new AiClassificationResult(
                ClassificationCategory.BUG, ClassificationPriority.HIGH, "npe on save"));
        AiClassificationResult r = fake.generateStructured("x", AiClassificationResult.class);
        assertThat(r.category()).isEqualTo(ClassificationCategory.BUG);
        assertThat(r.summary()).isEqualTo("npe on save");
    }

    @Test
    void timeout_mode_throws_timeout_on_generate() {
        FakeLlmClient fake = new FakeLlmClient().setMode(FakeLlmClient.Mode.TIMEOUT);
        assertThatThrownBy(() -> fake.generate("x")).isInstanceOf(LlmTimeoutException.class);
    }

    @Test
    void unavailable_mode_throws_unavailable_on_structured() {
        FakeLlmClient fake = new FakeLlmClient().setMode(FakeLlmClient.Mode.UNAVAILABLE);
        assertThatThrownBy(() -> fake.generateStructured("x", AiClassificationResult.class))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void provider_error_mode_throws_provider_error() {
        FakeLlmClient fake = new FakeLlmClient().setMode(FakeLlmClient.Mode.PROVIDER_ERROR);
        assertThatThrownBy(() -> fake.generateStructured("x", AiClassificationResult.class))
                .isInstanceOf(LlmProviderException.class);
    }
}
