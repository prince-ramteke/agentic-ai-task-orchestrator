package com.prince.agentic.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.user.Role;
import com.prince.agentic.user.RoleRepository;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PW = "SecurePassword123!";

    // --- helpers ---------------------------------------------------------

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

    // --- tests -----------------------------------------------------------

    @Test
    void create_returns201WithLocationAndBody() throws Exception {
        String token = registerAndLogin("t-create@example.com");
        mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Write plan\",\"status\":\"TODO\",\"priority\":\"HIGH\",\"estimatedHours\":2.5}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesRegex(".*/api/v1/tasks/\\d+")))
                .andExpect(jsonPath("$.title").value("Write plan"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void create_blankTitle_returns400() throws Exception {
        String token = registerAndLogin("t-blank@example.com");
        mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    void create_negativeEstimatedHours_returns400() throws Exception {
        String token = registerAndLogin("t-neg@example.com");
        mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"estimatedHours\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void create_ignoresClientSuppliedOwnerId() throws Exception {
        String token = registerAndLogin("t-owner@example.com");
        // Attempt to set ownerId=999999 — must be ignored; task belongs to the caller.
        MvcResult r = mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"ownerId\":999999}"))
                .andExpect(status().isCreated()).andReturn();
        long ownerId = objectMapper.readTree(r.getResponse().getContentAsString()).get("ownerId").asLong();
        long me = userRepository.findByEmail("t-owner@example.com").orElseThrow().getId();
        org.assertj.core.api.Assertions.assertThat(ownerId).isEqualTo(me).isNotEqualTo(999999L);
    }

    @Test
    void list_returnsOnlyCallersTasks_paginated() throws Exception {
        String a = registerAndLogin("t-list-a@example.com");
        String b = registerAndLogin("t-list-b@example.com");
        createTask(a, "A1");
        createTask(a, "A2");
        createTask(b, "B1");
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void list_badSortField_returns400() throws Exception {
        String token = registerAndLogin("t-sort@example.com");
        mockMvc.perform(get("/api/v1/tasks?sort=password,asc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void list_badStatusEnum_returns400() throws Exception {
        String token = registerAndLogin("t-enum@example.com");
        mockMvc.perform(get("/api/v1/tasks?status=BOGUS").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void getById_ownerGets200_nonOwnerGets404() throws Exception {
        String a = registerAndLogin("t-get-a@example.com");
        String b = registerAndLogin("t-get-b@example.com");
        long id = createTask(a, "secret");
        mockMvc.perform(get("/api/v1/tasks/" + id).header("Authorization", "Bearer " + a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("secret"));
        mockMvc.perform(get("/api/v1/tasks/" + id).header("Authorization", "Bearer " + b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        String token = registerAndLogin("t-missing@example.com");
        mockMvc.perform(get("/api/v1/tasks/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_fullReplacement_returns200() throws Exception {
        String token = registerAndLogin("t-upd@example.com");
        long id = createTask(token, "old");
        mockMvc.perform(put("/api/v1/tasks/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"new\",\"status\":\"COMPLETED\",\"priority\":\"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("new"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void update_missingStatus_returns400() throws Exception {
        String token = registerAndLogin("t-upd400@example.com");
        long id = createTask(token, "old");
        mockMvc.perform(put("/api/v1/tasks/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"new\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_nonOwner_returns404() throws Exception {
        String a = registerAndLogin("t-upd-a@example.com");
        String b = registerAndLogin("t-upd-b@example.com");
        long id = createTask(a, "x");
        mockMvc.perform(put("/api/v1/tasks/" + id).header("Authorization", "Bearer " + b)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"hijack\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_owner_returns204_thenGoneIs404() throws Exception {
        String token = registerAndLogin("t-del@example.com");
        long id = createTask(token, "x");
        mockMvc.perform(delete("/api/v1/tasks/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tasks/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_nonOwner_returns404_andDoesNotDelete() throws Exception {
        String a = registerAndLogin("t-del-a@example.com");
        String b = registerAndLogin("t-del-b@example.com");
        long id = createTask(a, "x");
        mockMvc.perform(delete("/api/v1/tasks/" + id).header("Authorization", "Bearer " + b))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tasks/" + id).header("Authorization", "Bearer " + a))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canGetAndUpdateAndDeleteAnotherUsersTask() throws Exception {
        String userToken = registerAndLogin("t-victim@example.com");
        String admin = adminToken("t-admin@example.com");
        long id = createTask(userToken, "user-task");

        mockMvc.perform(get("/api/v1/tasks/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tasks/" + id).header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"by-admin\",\"status\":\"CANCELLED\",\"priority\":\"LOW\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/tasks/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_listStillReturnsOnlyOwnTasks() throws Exception {
        String userToken = registerAndLogin("t-adminlist-u@example.com");
        String admin = adminToken("t-adminlist-a@example.com");
        createTask(userToken, "user-only");
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));   // own-list policy
    }

    @Test
    void allEndpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tasks/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/tasks/1").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/tasks/1")).andExpect(status().isUnauthorized());
    }
}
