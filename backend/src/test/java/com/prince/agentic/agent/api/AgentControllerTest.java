package com.prince.agentic.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.AgentOrchestrator;
import com.prince.agentic.agent.AgentResult;
import com.prince.agentic.agent.AgentStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer behavior of the agent endpoint with a mocked {@link AgentOrchestrator}: authentication,
 * input validation, and response mapping. No real orchestration or LLM is involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AgentOrchestrator orchestrator;

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
    void execute_returns200_withRunMetadata() throws Exception {
        String token = registerAndLogin("agent-exec@example.com");
        when(orchestrator.run(any(), any()))
                .thenReturn(new AgentResult("exec-1", AgentStatus.COMPLETED, "done", 2, 1, 12L, null));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCalls").value(1));
    }

    @Test
    void blankMessage_returns400() throws Exception {
        String token = registerAndLogin("agent-blank@example.com");
        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }
}
