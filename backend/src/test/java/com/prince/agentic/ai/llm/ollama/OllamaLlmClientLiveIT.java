package com.prince.agentic.ai.llm.ollama;

import com.prince.agentic.ai.dto.AiClassificationResult;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in end-to-end check against a real, running Ollama. Excluded from normal {@code mvn verify}:
 * it is a failsafe {@code *IT} and additionally gated by {@code -Dllm.live.ollama=true}, so CI and
 * everyday builds never depend on a model.
 *
 * <p>Uses the {@code test} profile (H2 for persistence) so the only live dependency is Ollama at
 * {@code localhost:11434} with the {@code llama3.2} model pulled. Run:
 * <pre>./mvnw -Dllm.live.ollama=true -Dit.test=OllamaLlmClientLiveIT verify</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "llm.live.ollama", matches = "true")
class OllamaLlmClientLiveIT {

    @Autowired
    private LlmClient llm;

    @Test
    void real_model_wired_behind_the_abstraction_is_ollama() {
        assertThat(llm).isInstanceOf(OllamaLlmClient.class);
        assertThat(llm.info().provider()).isEqualTo("ollama");
    }

    @Test
    void generate_returns_nonempty_text_from_real_model() {
        assertThat(llm.generate("Reply with a single word: pong")).isNotBlank();
    }

    /**
     * A small local model is not guaranteed to emit perfectly-typed structured output every time.
     * What we verify is our <b>contract</b>: the raw client either returns a fully-populated result,
     * or classifies bad output as {@link LlmInvalidOutputException} (never a raw/leaky error). The
     * bounded repair lives one layer up in {@code AiService}.
     */
    @Test
    void structured_output_is_either_valid_or_reported_as_invalid_output() {
        try {
            AiClassificationResult result = llm.generateStructured(
                    "Classify this: the application throws a NullPointerException on login",
                    AiClassificationResult.class);
            assertThat(result).isNotNull();
            assertThat(result.category()).isNotNull();
            assertThat(result.priority()).isNotNull();
        } catch (LlmInvalidOutputException expected) {
            // Acceptable: the model produced out-of-range structured output; our layer classified it correctly.
        }
    }
}
