package com.prince.agentic.ai.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean Validation rules for the M4 request and structured-output DTOs. */
class AiDtoValidationTest {

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

    @Test
    void generateRequest_blank_prompt_is_invalid() {
        assertThat(validator.validate(new AiGenerateRequest("  "))).isNotEmpty();
    }

    @Test
    void generateRequest_oversize_prompt_is_invalid() {
        assertThat(validator.validate(new AiGenerateRequest("a".repeat(4001)))).isNotEmpty();
    }

    @Test
    void generateRequest_valid_prompt_passes() {
        assertThat(validator.validate(new AiGenerateRequest("summarize this"))).isEmpty();
    }

    @Test
    void classifyRequest_blank_text_is_invalid() {
        assertThat(validator.validate(new AiClassifyRequest(""))).isNotEmpty();
    }

    @Test
    void classificationResult_null_fields_are_invalid() {
        assertThat(validator.validate(new AiClassificationResult(null, null, " "))).isNotEmpty();
    }

    @Test
    void classificationResult_complete_is_valid() {
        assertThat(validator.validate(new AiClassificationResult(
                ClassificationCategory.FEATURE, ClassificationPriority.LOW, "add dark mode"))).isEmpty();
    }
}
