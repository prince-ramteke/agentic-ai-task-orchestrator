package com.prince.agentic.audit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.agent.AgentStepKind;
import com.prince.agentic.agent.AgentStepOutcome;
import com.prince.agentic.agent.AgentToolOutcome;
import com.prince.agentic.audit.AgentExecutionRecord;
import com.prince.agentic.audit.AgentExecutionRepository;
import com.prince.agentic.audit.AgentStepRecord;
import com.prince.agentic.audit.AgentStepRepository;
import com.prince.agentic.audit.AuditExecutionStatus;
import com.prince.agentic.audit.ToolExecutionRecord;
import com.prince.agentic.audit.ToolExecutionRepository;
import com.prince.agentic.tool.ToolRiskLevel;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer behaviour of the audit read API on H2: owner-scoped list/detail shapes, cross-user masking
 * (404), unauthenticated (401), and the guarantee that no raw prompt/argument/output/secret is exposed.
 * Audit rows are seeded directly via the repositories in the test transaction (rolled back).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentAuditControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AgentExecutionRepository executions;
    @Autowired private AgentStepRepository steps;
    @Autowired private ToolExecutionRepository toolExecutions;

    private static final String PW = "SecurePassword123!";
    private static final Instant T = Instant.parse("2026-08-22T12:00:00Z");

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long userId(String email) {
        return userRepository.findByEmail(email.toLowerCase()).orElseThrow().getId();
    }

    private String seedExecution(long ownerId, String uid) {
        AgentExecutionRecord e = executions.saveAndFlush(new AgentExecutionRecord(
                uid, ownerId, "conv-1", "req-1", AuditExecutionStatus.COMPLETED, T));
        e.complete(AuditExecutionStatus.COMPLETED, null, "You have 2 tasks.", 2, 1,
                T.plusSeconds(1), 1000L);
        executions.saveAndFlush(e);
        AgentStepRecord step = steps.saveAndFlush(new AgentStepRecord(e.getId(), 0,
                AgentStepKind.TOOL_CALL, AgentStepOutcome.OK, "task.search", null, T, T, 5L));
        toolExecutions.saveAndFlush(new ToolExecutionRecord("te-" + uid, step.getId(), e.getId(), ownerId,
                "task.search", ToolRiskLevel.READ_ONLY, AgentToolOutcome.SUCCESS, null, null,
                "abc123hash", "found 2", T, T, 5L));
        return uid;
    }

    @Test
    void list_returnsOwnerExecutions_paginated() throws Exception {
        String token = registerAndLogin("audit-list@example.com");
        seedExecution(userId("audit-list@example.com"), "exec-aaa");

        mockMvc.perform(get("/api/v1/agent/executions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].executionId").value("exec-aaa"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detail_returnsStepsAndTools_withNoRawContent() throws Exception {
        String token = registerAndLogin("audit-detail@example.com");
        seedExecution(userId("audit-detail@example.com"), "exec-bbb");

        MvcResult r = mockMvc.perform(get("/api/v1/agent/executions/exec-bbb")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value("exec-bbb"))
                .andExpect(jsonPath("$.steps[0].type").value("TOOL_CALL"))
                .andExpect(jsonPath("$.toolExecutions[0].toolName").value("task.search"))
                .andExpect(jsonPath("$.toolExecutions[0].argumentsHash").value("abc123hash"))
                .andReturn();
        // The response must not carry internal fields, raw args, or Java class hints.
        String body = r.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("ownerId").doesNotContain("password").doesNotContain("com.prince");
    }

    @Test
    void detail_crossUser_isMasked404() throws Exception {
        String owner = registerAndLogin("audit-owner@example.com");
        String other = registerAndLogin("audit-other@example.com");
        seedExecution(userId("audit-owner@example.com"), "exec-secret");

        // User B requests A's execution id → masked 404 (never reveals it exists).
        mockMvc.perform(get("/api/v1/agent/executions/exec-secret")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EXECUTION_NOT_FOUND"));
        // And it is visible to its owner.
        mockMvc.perform(get("/api/v1/agent/executions/exec-secret")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/agent/executions")).andExpect(status().isUnauthorized());
    }
}
