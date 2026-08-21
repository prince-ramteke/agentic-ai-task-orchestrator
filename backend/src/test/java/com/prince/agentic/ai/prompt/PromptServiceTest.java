package com.prince.agentic.ai.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies templates load and bind the untrusted input into the delimited slot only. */
class PromptServiceTest {

    private PromptService service() {
        ResourceLoader loader = new DefaultResourceLoader();
        return new PromptService(
                loader.getResource("classpath:prompts/generate.st"),
                loader.getResource("classpath:prompts/classify.st"));
    }

    @Test
    void renderGenerate_embeds_input_within_delimiters() {
        String out = service().renderGenerate("hello world");
        assertThat(out).contains("hello world").contains("<<<").contains(">>>");
    }

    @Test
    void renderGenerate_keeps_instruction_text_present() {
        assertThat(service().renderGenerate("x")).containsIgnoringCase("concise, helpful assistant");
    }

    @Test
    void renderClassify_includes_input_and_categories() {
        String out = service().renderClassify("app crashes on login");
        assertThat(out).contains("app crashes on login")
                .contains("BUG").contains("FEATURE").contains("priority");
    }

    @Test
    void renderClassify_null_input_is_rendered_as_empty_not_literal_token() {
        String out = service().renderClassify(null);
        assertThat(out).doesNotContain("{input}");
    }
}
