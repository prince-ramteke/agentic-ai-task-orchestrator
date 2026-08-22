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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end M7 memory over the full stack: authenticated HTTP → {@link AgentConversationService} →
 * real {@link AgentOrchestrator} → {@link ScriptedLlmClient} (deterministic, no Ollama) → real tools →
 * real PostgreSQL + real Redis (Testcontainers, via {@link AbstractPostgresIntegrationTest}).
 *
 * <p>The central proof (spec §31): the SECOND turn's LLM prompt must contain the FIRST turn's bounded
 * context — verified against the prompts captured by {@link ScriptedLlmClient}, not merely by checking
 * that Redis holds messages.
 */
@AutoConfigureMockMvc
@Transactional
class AgentConversationIT extends AbstractPostgresIntegrationTest {

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

    private void createTask(String token, String title) throws Exception {
        mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated());
    }

    private MvcResult execute(String token, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        r.getResponse().setCharacterEncoding("UTF-8");
        return r;
    }

    private String field(MvcResult r, String name) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).get(name).asText();
    }

    // --- the multi-turn proof -------------------------------------------------

    @Test
    void secondTurn_receivesFirstTurnContext() throws Exception {
        String a = registerAndLogin("conv-multiturn@example.com");
        createTask(a, "Alpha");
        createTask(a, "Beta");

        // Turn 1 consumes two decisions (search → final); turn 2 consumes the third (final).
        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "HIGH")),
                new AgentDecision(AgentAction.FINAL, "You have 2.", null, null),
                new AgentDecision(AgentAction.FINAL, "Alpha is due first.", null, null));

        MvcResult turn1 = execute(a, "{\"message\":\"Show my high priority tasks.\"}");
        String conversationId = field(turn1, "conversationId");
        assertThat(conversationId).isNotBlank();
        assertThat(field(turn1, "memoryStatus")).isEqualTo("ACTIVE");

        MvcResult turn2 = execute(a, "{\"message\":\"Which one is due first?\","
                + "\"conversationId\":\"" + conversationId + "\"}");
        assertThat(field(turn2, "conversationId")).isEqualTo(conversationId);
        assertThat(field(turn2, "response")).isEqualTo("Alpha is due first.");

        // The decisive assertion: turn 2's prompt (the 3rd captured) carries turn 1's bounded context.
        assertThat(scriptedLlm.prompts()).hasSize(3);
        String secondTurnPrompt = scriptedLlm.prompts().get(2);
        assertThat(secondTurnPrompt)
                .contains("Show my high priority tasks.")   // turn-1 user message
                .contains("You have 2.")                    // turn-1 assistant response
                .contains("Which one is due first?");       // current message
    }

    @Test
    void crossUser_cannotContinueAnotherUsersConversation() throws Exception {
        String a = registerAndLogin("conv-owner@example.com");
        scriptedLlm.enqueueStructured(new AgentDecision(AgentAction.FINAL, "ok", null, null));
        MvcResult turn1 = execute(a, "{\"message\":\"hello\"}");
        String conversationId = field(turn1, "conversationId");

        String b = registerAndLogin("conv-attacker@example.com");
        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"leak it\",\"conversationId\":\"" + conversationId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void noConversationId_backwardCompatible_stillCompletes() throws Exception {
        String a = registerAndLogin("conv-stateless@example.com");
        scriptedLlm.enqueueStructured(new AgentDecision(AgentAction.FINAL, "done", null, null));
        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.memoryStatus").value("ACTIVE"));
    }

    @Test
    void deleteConversation_thenReuse_returns404() throws Exception {
        String a = registerAndLogin("conv-delete@example.com");
        scriptedLlm.enqueueStructured(new AgentDecision(AgentAction.FINAL, "ok", null, null));
        String conversationId = field(execute(a, "{\"message\":\"start\"}"), "conversationId");

        mockMvc.perform(delete("/api/v1/agent/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + a))
                .andExpect(status().isNoContent());

        scriptedLlm.enqueueStructured(new AgentDecision(AgentAction.FINAL, "again", null, null));
        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"resume\",\"conversationId\":\"" + conversationId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }
}
