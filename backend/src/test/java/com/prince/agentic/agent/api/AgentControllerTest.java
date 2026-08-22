package com.prince.agentic.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.AgentConfirmationOutcome;
import com.prince.agentic.agent.AgentConfirmationService;
import com.prince.agentic.agent.AgentConversationService;
import com.prince.agentic.agent.AgentResult;
import com.prince.agentic.agent.AgentStatus;
import com.prince.agentic.agent.ConversationOutcome;
import com.prince.agentic.agent.MemoryStatus;
import com.prince.agentic.guardrail.confirmation.PendingAction;
import com.prince.agentic.guardrail.confirmation.PendingConfirmation;
import com.prince.agentic.guardrail.exception.ConfirmationNotFoundException;
import com.prince.agentic.memory.exception.ConversationNotFoundException;
import com.prince.agentic.tool.ToolRiskLevel;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer behavior of the agent endpoint with a mocked {@link AgentConversationService}:
 * authentication, input validation, response mapping (incl. conversationId + memoryStatus), and the
 * DELETE conversation route. No real orchestration, memory, or LLM is involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AgentConversationService conversationService;
    @MockitoBean private AgentConfirmationService confirmationService;

    private static final String PW = "SecurePassword123!";
    private static final String A_UUID = "11111111-1111-1111-1111-111111111111";

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private AgentResult completed() {
        return new AgentResult("exec-1", AgentStatus.COMPLETED, "done", 2, 1, 12L, null, List.of());
    }

    @Test
    void execute_returns200_withRunMetadataConversationIdAndMemoryStatus() throws Exception {
        String token = registerAndLogin("agent-exec@example.com");
        when(conversationService.execute(any(), any(), any()))
                .thenReturn(new ConversationOutcome(completed(), A_UUID, MemoryStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCalls").value(1))
                .andExpect(jsonPath("$.conversationId").value(A_UUID))
                .andExpect(jsonPath("$.memoryStatus").value("ACTIVE"));
    }

    @Test
    void execute_pendingConfirmation_exposesSafeConfirmationFields() throws Exception {
        String token = registerAndLogin("agent-pending@example.com");
        AgentResult pending = new AgentResult("exec-2", AgentStatus.PENDING_CONFIRMATION, null, 1, 0,
                3L, "CONFIRMATION_REQUIRED", List.of(),
                new PendingAction("task.create", java.util.Map.of("title", "x"), ToolRiskLevel.SIDE_EFFECTING));
        PendingConfirmation pc = new PendingConfirmation("conf-1", "task.create",
                ToolRiskLevel.SIDE_EFFECTING, "Run tool 'task.create' (SIDE_EFFECTING).",
                java.time.Instant.parse("2026-08-22T12:05:00Z"));
        when(conversationService.execute(any(), any(), any()))
                .thenReturn(new ConversationOutcome(pending, A_UUID, MemoryStatus.ACTIVE, pc));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"create a task\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.confirmationId").value("conf-1"))
                .andExpect(jsonPath("$.confirmationTool").value("task.create"))
                .andExpect(jsonPath("$.confirmationRiskLevel").value("SIDE_EFFECTING"))
                .andExpect(jsonPath("$.confirmationSummary").exists());
    }

    @Test
    void confirm_returns200_withExecutionSummary() throws Exception {
        String token = registerAndLogin("agent-confirm@example.com");
        when(confirmationService.confirm(any(), any()))
                .thenReturn(new AgentConfirmationOutcome("conf-1", "task.create", true, "{\"id\":42}", null));

        mockMvc.perform(post("/api/v1/agent/confirmations/conf-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.tool").value("task.create"));
    }

    @Test
    void confirm_missing_returns404_masked() throws Exception {
        String token = registerAndLogin("agent-confirm404@example.com");
        when(confirmationService.confirm(any(), any())).thenThrow(new ConfirmationNotFoundException());

        mockMvc.perform(post("/api/v1/agent/confirmations/conf-x")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONFIRMATION_NOT_FOUND"));
    }

    @Test
    void confirm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/confirmations/conf-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelConfirmation_returns204() throws Exception {
        String token = registerAndLogin("agent-cancel@example.com");
        mockMvc.perform(delete("/api/v1/agent/confirmations/conf-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void blankMessage_returns400() throws Exception {
        String token = registerAndLogin("agent-blank@example.com");
        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonUuidConversationId_returns400_beforeAnyLookup() throws Exception {
        String token = registerAndLogin("agent-badid@example.com");
        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\",\"conversationId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteConversation_returns204() throws Exception {
        String token = registerAndLogin("agent-del@example.com");
        mockMvc.perform(delete("/api/v1/agent/conversations/" + A_UUID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteConversation_missing_returns404() throws Exception {
        String token = registerAndLogin("agent-del404@example.com");
        doThrow(new ConversationNotFoundException())
                .when(conversationService).delete(any(), any());
        mockMvc.perform(delete("/api/v1/agent/conversations/" + A_UUID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void deleteConversation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/agent/conversations/" + A_UUID))
                .andExpect(status().isUnauthorized());
    }
}
