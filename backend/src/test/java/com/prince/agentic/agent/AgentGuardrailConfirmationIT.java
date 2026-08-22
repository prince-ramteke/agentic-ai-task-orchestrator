package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.support.ScriptedLlmClient;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end guardrail confirmation flow (spec §16, §29) against real PostgreSQL + Redis
 * (Testcontainers): authenticated HTTP → real {@link AgentOrchestrator} → scripted LLM proposing the
 * SIDE_EFFECTING {@code task.create} → {@code GuardrailEngine} halts at PENDING_CONFIRMATION → real
 * Redis-backed confirmation → {@code POST /confirmations/{id}} → real {@code ToolExecutor} →
 * {@code TaskService} → PostgreSQL. Verifies the task is created <b>exactly once</b>, and that a
 * replay does not create a second.
 *
 * <p>NOT {@code @Transactional}: the confirmation lives in Redis across two separate HTTP requests, so
 * the created row must actually commit for the count assertion to be meaningful.
 */
@AutoConfigureMockMvc
class AgentGuardrailConfirmationIT extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    static class ScriptedLlmConfig {
        @Bean
        @Primary
        LlmClient scriptedLlm() {
            return new ScriptedLlmClient();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LlmClient llmClient;

    private ScriptedLlmClient scriptedLlm;

    private static final String PW = "SecurePassword123!";

    @BeforeEach
    void resetScriptedLlm() {
        scriptedLlm = (ScriptedLlmClient) llmClient;
        scriptedLlm.reset();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long taskCount(String token) throws Exception {
        MvcResult r = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("totalElements").asLong();
    }

    @Test
    void sideEffectTool_requiresConfirmation_thenExecutesExactlyOnce() throws Exception {
        String token = registerAndLogin("guard-e2e-once@example.com");
        assertThat(taskCount(token)).isZero();

        // The model proposes a SIDE_EFFECTING create; the guardrail must halt before any effect.
        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.create",
                        Map.of("title", "review the quarterly report")));

        MvcResult exec = mockMvc.perform(post("/api/v1/agent/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"create a task to review the report\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.confirmationTool").value("task.create"))
                .andExpect(jsonPath("$.confirmationId").exists())
                .andReturn();

        // No task was created by merely proposing it.
        assertThat(taskCount(token)).isZero();

        String confirmationId = objectMapper.readTree(exec.getResponse().getContentAsString())
                .get("confirmationId").asText();

        // Confirm → the exact stored action runs exactly once.
        mockMvc.perform(post("/api/v1/agent/confirmations/" + confirmationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.tool").value("task.create"));

        assertThat(taskCount(token)).isEqualTo(1);

        // Replay the same confirmation → rejected (single-use); no second task is created.
        mockMvc.perform(post("/api/v1/agent/confirmations/" + confirmationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());

        assertThat(taskCount(token)).isEqualTo(1);
    }

    @Test
    void confirmation_isOwnerScoped_crossUserRejected_andNoEffect() throws Exception {
        String userA = registerAndLogin("guard-e2e-a@example.com");
        String userB = registerAndLogin("guard-e2e-b@example.com");

        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.create",
                        Map.of("title", "A's private task")));

        MvcResult exec = mockMvc.perform(post("/api/v1/agent/execute")
                        .header("Authorization", "Bearer " + userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"create a task\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andReturn();
        String confirmationId = objectMapper.readTree(exec.getResponse().getContentAsString())
                .get("confirmationId").asText();

        // User B presents A's confirmation id → masked 404; no task is created for either user.
        mockMvc.perform(post("/api/v1/agent/confirmations/" + confirmationId)
                        .header("Authorization", "Bearer " + userB))
                .andExpect(status().isNotFound());
        assertThat(taskCount(userB)).isZero();

        // A can still confirm their own action exactly once.
        mockMvc.perform(post("/api/v1/agent/confirmations/" + confirmationId)
                        .header("Authorization", "Bearer " + userA))
                .andExpect(status().isOk());
        assertThat(taskCount(userA)).isEqualTo(1);
    }
}
