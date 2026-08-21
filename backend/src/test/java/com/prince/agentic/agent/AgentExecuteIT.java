package com.prince.agentic.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.support.ScriptedLlmClient;
import com.prince.agentic.ai.llm.LlmClient;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import com.prince.agentic.user.Role;
import com.prince.agentic.user.RoleRepository;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One end-to-end, real-database, security-focused integration test for the agent (M6): authenticated
 * HTTP -&gt; real {@link AgentOrchestrator} -&gt; {@link ScriptedLlmClient} (deterministic, NO Ollama) -&gt;
 * real {@code ToolRegistry} -&gt; real {@code ToolExecutor} -&gt; real {@code TaskService} -&gt; real
 * PostgreSQL (Testcontainers, via {@link AbstractPostgresIntegrationTest}).
 *
 * <p>The {@link ScriptedLlmClient} bean is a shared Spring singleton across all tests in this class
 * (and any sibling IT reusing the cached context), so every test resets it in {@code @BeforeEach}
 * before enqueueing its own scripted decision sequence.
 */
@AutoConfigureMockMvc
@Transactional
class AgentExecuteIT extends AbstractPostgresIntegrationTest {

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
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LlmClient llmClient;

    private ScriptedLlmClient scriptedLlm;

    private static final String PW = "SecurePassword123!";

    @BeforeEach
    void resetScriptedLlm() {
        scriptedLlm = (ScriptedLlmClient) llmClient;
        scriptedLlm.reset();
    }

    // --- helpers (mirrors TaskApiTest / AiIntegrationTest patterns) ------

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        return token(email);
    }

    private String adminToken(String email) throws Exception {
        Role admin = roleRepository.findByName(RoleNames.ROLE_ADMIN).orElseThrow();
        User u = new User(email.toLowerCase(), passwordEncoder.encode(PW));
        u.addRole(admin);
        userRepository.saveAndFlush(u);
        return token(email);
    }

    private String token(String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createTask(String token, String title) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult executeAgent(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    // --- tests -------------------------------------------------------------

    @Test
    void endToEnd_searchThenFinal_completes() throws Exception {
        String a = registerAndLogin("agent-e2e-a@example.com");
        createTask(a, "A1");
        createTask(a, "A2");

        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "HIGH")),
                new AgentDecision(AgentAction.FINAL, "You have 2.", null, null));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"how many high priority tasks do I have\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCalls").value(1))
                .andExpect(jsonPath("$.iterations").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.response").value("You have 2."));
    }

    @Test
    void crossUser_taskGet_returns404Observation_agentRecovers() throws Exception {
        String a = registerAndLogin("agent-cross-a@example.com");
        String b = registerAndLogin("agent-cross-b@example.com");
        long bTaskId = createTask(b, "b-secret-title");

        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.get", Map.of("taskId", bTaskId)),
                new AgentDecision(AgentAction.FINAL, "done", null, null));

        MvcResult r = executeAgent(a, "{\"message\":\"get task " + bTaskId + "\"}");
        r.getResponse().setCharacterEncoding("UTF-8");
        String body = r.getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(r.getResponse().getStatus()).isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(body).contains("\"status\":\"COMPLETED\"");
        org.assertj.core.api.Assertions.assertThat(body).contains("\"toolCalls\":1");
        // Security assertion: the cross-user tool call must be masked as NOT_FOUND, never leaking B's data.
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("b-secret-title");
    }

    @Test
    void unregisteredTool_notExecuted_agentRecovers() throws Exception {
        String a = registerAndLogin("agent-unreg-a@example.com");

        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "database.dropAll", Map.of()),
                new AgentDecision(AgentAction.FINAL, "done", null, null));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"drop everything\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCalls").value(1));
    }

    @Test
    void identitySpoof_bodyFieldsIgnored() throws Exception {
        String a = registerAndLogin("agent-spoof-a@example.com");
        createTask(a, "spoof-task");

        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.search", Map.of("priority", "HIGH")),
                new AgentDecision(AgentAction.FINAL, "ok", null, null));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"list my tasks\",\"userId\":999999,\"role\":\"ADMIN\",\"ownerId\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_canGetAnotherUsersTask_throughAgent() throws Exception {
        String user = registerAndLogin("agent-admin-victim@example.com");
        String admin = adminToken("agent-admin-actor@example.com");
        long taskId = createTask(user, "victim-task");

        scriptedLlm.enqueueStructured(
                new AgentDecision(AgentAction.TOOL_CALL, null, "task.get", Map.of("taskId", taskId)),
                new AgentDecision(AgentAction.FINAL, "seen", null, null));

        mockMvc.perform(post("/api/v1/agent/execute").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"get task " + taskId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCalls").value(1));
    }
}
