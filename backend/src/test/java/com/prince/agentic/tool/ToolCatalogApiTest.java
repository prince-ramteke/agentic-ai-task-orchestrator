package com.prince.agentic.tool;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/tools: ADMIN-only, metadata-only, no implementation class names. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ToolCatalogApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PW = "SecurePassword123!";

    private String userToken(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String adminToken(String email) throws Exception {
        Role admin = roleRepository.findByName(RoleNames.ROLE_ADMIN).orElseThrow();
        User u = new User(email.toLowerCase(), passwordEncoder.encode(PW));
        u.addRole(admin);
        userRepository.saveAndFlush(u);
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void admin_gets_tool_metadata_without_class_names() throws Exception {
        String token = adminToken("tools-admin@example.com");
        MvcResult r = mockMvc.perform(get("/api/v1/tools").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = r.getResponse().getContentAsString();
        assertThat(body).contains("task.get").contains("math.calculate")
                .contains("READ_ONLY").contains("TaskGetInput");
        // No implementation class names leaked.
        assertThat(body).doesNotContain("TaskGetTool").doesNotContain("CalculatorTool");

        JsonNode arr = objectMapper.readTree(body);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(6);
    }

    @Test
    void user_is_forbidden() throws Exception {
        String token = userToken("tools-user@example.com");
        mockMvc.perform(get("/api/v1/tools").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void anonymous_is_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isUnauthorized());
    }
}
