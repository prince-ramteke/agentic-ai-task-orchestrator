package com.prince.agentic.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.AgentAction;
import com.prince.agentic.agent.AgentDecision;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end durable audit (spec §16, §33): authenticated HTTP → real {@link com.prince.agentic.agent.AgentOrchestrator}
 * → scripted LLM (no Ollama) → real guardrail/tool → best-effort {@code AuditService} → PostgreSQL,
 * read back through {@code GET /api/v1/agent/executions}. Verifies a completed run and the confirm flow
 * (PENDING_CONFIRMATION → confirm → CONFIRMATION_APPROVED step + tool execution + promotion), plus
 * owner isolation. NOT {@code @Transactional}: audit rows must actually commit to be queryable.
 */
@AutoConfigureMockMvc
class AgentAuditE2EIT extends AbstractPostgresIntegrationTest {

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
    void reset() {
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

    private String field(MvcResult r, String name) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).get(name).asText();
    }

    @Test
    void completedRun_isAudited_withStepsAndToolExecution() throws Exception {
        String token = registerAndLogin("audit-e2e-run@example.com");
        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "HIGH")),
                new AgentDecision(AgentAction.FINAL, "You have 0 high tasks.", null, null));

        MvcResult exec = mockMvc.perform(post("/api/v1/agent/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"how many high tasks\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        String executionId = field(exec, "executionId");

        mockMvc.perform(get("/api/v1/agent/executions/" + executionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCalls").value(1))
                .andExpect(jsonPath("$.finalResponseSummary").value("You have 0 high tasks."))
                .andExpect(jsonPath("$.steps[?(@.type=='TOOL_CALL')]").isNotEmpty())
                .andExpect(jsonPath("$.toolExecutions[0].toolName").value("task.search"))
                .andExpect(jsonPath("$.toolExecutions[0].outcome").value("SUCCESS"));

        // It also appears in the owner's list.
        mockMvc.perform(get("/api/v1/agent/executions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void confirmationFlow_isAudited_andPromotesExecution() throws Exception {
        String token = registerAndLogin("audit-e2e-confirm@example.com");
        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.create", Map.of("title", "review report")));

        MvcResult exec = mockMvc.perform(post("/api/v1/agent/execute")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"create a task\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andReturn();
        String executionId = field(exec, "executionId");
        String confirmationId = field(exec, "confirmationId");

        // Before confirming: the run is durably PENDING_CONFIRMATION with a CONFIRMATION_REQUIRED step.
        mockMvc.perform(get("/api/v1/agent/executions/" + executionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.steps[?(@.type=='CONFIRMATION_REQUIRED')]").isNotEmpty());

        mockMvc.perform(post("/api/v1/agent/confirmations/" + confirmationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        // After confirming: promoted to COMPLETED with an appended CONFIRMATION_APPROVED step + tool exec.
        mockMvc.perform(get("/api/v1/agent/executions/" + executionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps[?(@.type=='CONFIRMATION_APPROVED')]").isNotEmpty())
                .andExpect(jsonPath("$.toolExecutions[?(@.toolName=='task.create')]").isNotEmpty());
    }

    @Test
    void crossUser_cannotReadAnotherUsersExecution() throws Exception {
        String a = registerAndLogin("audit-e2e-a@example.com");
        String b = registerAndLogin("audit-e2e-b@example.com");
        scriptedLlm.enqueueStructured(new AgentDecision(AgentAction.FINAL, "hi", null, null));

        MvcResult exec = mockMvc.perform(post("/api/v1/agent/execute")
                        .header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk()).andReturn();
        String executionId = field(exec, "executionId");

        mockMvc.perform(get("/api/v1/agent/executions/" + executionId)
                        .header("Authorization", "Bearer " + b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EXECUTION_NOT_FOUND"));
    }
}
