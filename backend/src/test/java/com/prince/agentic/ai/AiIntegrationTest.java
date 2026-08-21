package com.prince.agentic.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.ai.dto.AiClassificationResult;
import com.prince.agentic.ai.dto.ClassificationCategory;
import com.prince.agentic.ai.dto.ClassificationPriority;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.ai.support.FakeLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration through the real AiService, with a {@link FakeLlmClient} standing in for
 * the model (marked {@code @Primary} so AiService uses it over the Ollama client). Proves the app
 * boots with Spring AI's Ollama auto-config present and serves both AI endpoints end-to-end
 * <b>without any running Ollama</b> — the payoff of the LlmClient abstraction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiIntegrationTest {

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        LlmClient fakeLlmClient() {
            return new FakeLlmClient()
                    .setText("A deterministic completion.")
                    .setStructured(new AiClassificationResult(
                            ClassificationCategory.BUG, ClassificationPriority.HIGH, "login throws NPE"));
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String PW = "SecurePassword123!";

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void generate_endpoint_returns_completion_from_fake_provider() throws Exception {
        String token = registerAndLogin("it-gen@example.com");
        mockMvc.perform(post("/api/v1/ai/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"say hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("A deterministic completion."))
                .andExpect(jsonPath("$.provider").value("fake"))
                .andExpect(jsonPath("$.model").value("fake-model"));
    }

    @Test
    void classify_endpoint_returns_typed_validated_result() throws Exception {
        String token = registerAndLogin("it-cls@example.com");
        mockMvc.perform(post("/api/v1/ai/classify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"login throws a NullPointerException\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BUG"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.summary").value("login throws NPE"))
                .andExpect(jsonPath("$.provider").value("fake"));
    }

    @Test
    void ai_endpoints_require_authentication() throws Exception {
        mockMvc.perform(post("/api/v1/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }
}
