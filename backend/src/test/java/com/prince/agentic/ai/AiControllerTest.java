package com.prince.agentic.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.ai.dto.AiClassificationResponse;
import com.prince.agentic.ai.dto.AiGenerateResponse;
import com.prince.agentic.ai.dto.ClassificationCategory;
import com.prince.agentic.ai.dto.ClassificationPriority;
import com.prince.agentic.ai.llm.exception.LlmInvalidOutputException;
import com.prince.agentic.ai.llm.exception.LlmTimeoutException;
import com.prince.agentic.ai.llm.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer behavior of the AI endpoints with a mocked AiService: authentication, input validation,
 * and the LLM error → envelope mapping. No model is involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AiService aiService;

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
    void generate_returns_200_with_content() throws Exception {
        String token = registerAndLogin("ai-gen@example.com");
        when(aiService.generateText("hi")).thenReturn(new AiGenerateResponse("hello", "llama3.2", "ollama"));

        mockMvc.perform(post("/api/v1/ai/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello"))
                .andExpect(jsonPath("$.model").value("llama3.2"))
                .andExpect(jsonPath("$.provider").value("ollama"));
    }

    @Test
    void classify_returns_200_with_typed_result() throws Exception {
        String token = registerAndLogin("ai-cls@example.com");
        when(aiService.classify(anyString())).thenReturn(new AiClassificationResponse(
                ClassificationCategory.BUG, ClassificationPriority.HIGH, "crash on login", "llama3.2", "ollama"));

        mockMvc.perform(post("/api/v1/ai/classify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"it crashes on login\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BUG"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.provider").value("ollama"));
    }

    @Test
    void generate_blank_prompt_returns_400() throws Exception {
        String token = registerAndLogin("ai-blank@example.com");
        mockMvc.perform(post("/api/v1/ai/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generate_oversize_prompt_returns_400() throws Exception {
        String token = registerAndLogin("ai-big@example.com");
        String big = "a".repeat(4001);
        mockMvc.perform(post("/api/v1/ai/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"" + big + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void generate_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void classify_provider_unavailable_returns_503_envelope() throws Exception {
        String token = registerAndLogin("ai-503@example.com");
        when(aiService.classify(anyString())).thenThrow(new LlmUnavailableException("down"));

        mockMvc.perform(post("/api/v1/ai/classify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"crashes on login\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void generate_timeout_returns_504_envelope() throws Exception {
        String token = registerAndLogin("ai-504@example.com");
        when(aiService.generateText(anyString())).thenThrow(new LlmTimeoutException("slow"));

        mockMvc.perform(post("/api/v1/ai/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("LLM_TIMEOUT"));
    }

    @Test
    void classify_invalid_output_returns_422_envelope() throws Exception {
        String token = registerAndLogin("ai-422@example.com");
        when(aiService.classify(anyString())).thenThrow(new LlmInvalidOutputException("bad"));

        mockMvc.perform(post("/api/v1/ai/classify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("LLM_INVALID_OUTPUT"));
    }
}
