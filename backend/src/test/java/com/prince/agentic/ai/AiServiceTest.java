package com.prince.agentic.ai;

import com.prince.agentic.ai.dto.AiClassificationResponse;
import com.prince.agentic.ai.dto.AiClassificationResult;
import com.prince.agentic.ai.dto.AiGenerateResponse;
import com.prince.agentic.ai.dto.ClassificationCategory;
import com.prince.agentic.ai.dto.ClassificationPriority;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.llm.LlmProviderInfo;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import com.prince.agentic.ai.llm.exception.LlmProviderException;
import com.prince.agentic.ai.prompt.PromptService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiService orchestration behavior with a mocked LlmClient and a real PromptService + Validator.
 * No network, deterministic.
 */
class AiServiceTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private PromptService prompts() {
        ResourceLoader loader = new DefaultResourceLoader();
        return new PromptService(
                loader.getResource("classpath:prompts/generate.st"),
                loader.getResource("classpath:prompts/classify.st"));
    }

    private AiService service(LlmClient llm) {
        return new AiService(llm, prompts(), validator, new SimpleMeterRegistry());
    }

    @Test
    void generateText_returns_content_and_metadata() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generate(anyString())).thenReturn("hello");
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        AiGenerateResponse out = service(llm).generateText("hi");

        assertThat(out.content()).isEqualTo("hello");
        assertThat(out.model()).isEqualTo("llama3.2");
        assertThat(out.provider()).isEqualTo("ollama");
    }

    @Test
    void classify_valid_output_is_returned_with_metadata_and_no_repair() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenReturn(new AiClassificationResult(
                        ClassificationCategory.BUG, ClassificationPriority.HIGH, "crash on login"));
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        AiClassificationResponse out = service(llm).classify("it crashes");

        assertThat(out.category()).isEqualTo(ClassificationCategory.BUG);
        assertThat(out.priority()).isEqualTo(ClassificationPriority.HIGH);
        assertThat(out.model()).isEqualTo("llama3.2");
        verify(llm, times(1)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }

    @Test
    void classify_invalid_then_valid_triggers_exactly_one_repair() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenReturn(new AiClassificationResult(null, null, " "))                                     // invalid
                .thenReturn(new AiClassificationResult(
                        ClassificationCategory.OTHER, ClassificationPriority.LOW, "ok"));                    // repaired
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        AiClassificationResponse out = service(llm).classify("text");

        assertThat(out.summary()).isEqualTo("ok");
        verify(llm, times(2)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }

    @Test
    void classify_null_output_then_valid_is_repaired() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenReturn(null)                                                                            // empty/unparseable
                .thenReturn(new AiClassificationResult(
                        ClassificationCategory.QUESTION, ClassificationPriority.MEDIUM, "how do I reset?"));
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        assertThat(service(llm).classify("text").category()).isEqualTo(ClassificationCategory.QUESTION);
        verify(llm, times(2)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }

    @Test
    void classify_first_attempt_throws_invalid_output_then_repair_succeeds() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenThrow(new LlmInvalidOutputException("unparseable enum"))                          // thrown parse failure
                .thenReturn(new AiClassificationResult(
                        ClassificationCategory.FEATURE, ClassificationPriority.MEDIUM, "add export"));  // repaired
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        AiClassificationResponse out = service(llm).classify("text");

        assertThat(out.category()).isEqualTo(ClassificationCategory.FEATURE);
        verify(llm, times(2)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }

    @Test
    void classify_thrown_invalid_output_twice_throws_invalid_output() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenThrow(new LlmInvalidOutputException("unparseable enum"));
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        assertThatThrownBy(() -> service(llm).classify("text"))
                .isInstanceOf(LlmInvalidOutputException.class);
        verify(llm, times(2)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }

    @Test
    void classify_invalid_twice_throws_invalid_output() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenReturn(new AiClassificationResult(null, null, " "));
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        assertThatThrownBy(() -> service(llm).classify("text"))
                .isInstanceOf(LlmInvalidOutputException.class);
        verify(llm, times(2)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }

    @Test
    void classify_provider_error_propagates_without_repair() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStructured(anyString(), eq(AiClassificationResult.class)))
                .thenThrow(new LlmProviderException("boom", null));
        when(llm.info()).thenReturn(new LlmProviderInfo("ollama", "llama3.2"));

        assertThatThrownBy(() -> service(llm).classify("text"))
                .isInstanceOf(LlmProviderException.class);
        verify(llm, times(1)).generateStructured(anyString(), eq(AiClassificationResult.class));
    }
}
