package com.prince.agentic.auth;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security tests exercising the real filter chain (JWT authentication →
 * authorization → controller) via MockMvc against H2 running the production Flyway migrations.
 * Each test runs in a rolled-back transaction for isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "SecurePassword123!";

    // ---------------------------------------------------------------- registration

    @Test
    void register_validUser_returns201WithUserRoleAndNoHash() throws Exception {
        mockMvc.perform(register("newuser@example.com", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("ROLE_USER")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                // The hash must never appear in a response.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(register("dupe@example.com", PASSWORD)).andExpect(status().isCreated());
        mockMvc.perform(register("dupe@example.com", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_emailIsCaseInsensitiveForDuplicates() throws Exception {
        mockMvc.perform(register("Mixed@Example.com", PASSWORD)).andExpect(status().isCreated());
        mockMvc.perform(register("mixed@example.com", PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(register("not-an-email", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(register("shortpw@example.com", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void register_clientCannotSelfAssignAdminRole() throws Exception {
        // Extra 'role' field is ignored (unknown property); the user is created as ROLE_USER only.
        String body = "{\"email\":\"sneaky@example.com\",\"password\":\"" + PASSWORD
                + "\",\"role\":\"ROLE_ADMIN\",\"roles\":[\"ROLE_ADMIN\"]}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("ROLE_USER")));

        User saved = userRepository.findByEmail("sneaky@example.com").orElseThrow();
        assertThat(saved.getRoles()).extracting(Role::getName).containsExactly(RoleNames.ROLE_USER);
    }

    @Test
    void register_storesBcryptHashNotRawPassword() throws Exception {
        mockMvc.perform(register("hashcheck@example.com", PASSWORD)).andExpect(status().isCreated());

        User saved = userRepository.findByEmail("hashcheck@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(saved.getPasswordHash()).startsWith("$2");            // BCrypt marker
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void uniqueEmailConstraint_isEnforcedAtDatabaseLevel() {
        userRepository.saveAndFlush(new User("constraint@example.com", "$2a$dummyhashvalue"));
        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new User("constraint@example.com", "$2a$anotherhash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- login

    @Test
    void login_validCredentials_returnsBearerToken() throws Exception {
        mockMvc.perform(register("login-ok@example.com", PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(login("login-ok@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void login_wrongPassword_returns401Generic() throws Exception {
        mockMvc.perform(register("login-bad@example.com", PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(login("login-bad@example.com", "WrongPassword123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void login_unknownUser_returnsSameGeneric401AsWrongPassword() throws Exception {
        // No enumeration: unknown identity yields the identical error as a wrong password.
        mockMvc.perform(login("ghost@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    // ---------------------------------------------------------------- JWT validation on protected routes

    @Test
    void protectedEndpoint_withValidToken_returns200AndIdentity() throws Exception {
        mockMvc.perform(register("me@example.com", PASSWORD)).andExpect(status().isCreated());
        String token = loginToken("me@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("ROLE_USER")))
                .andExpect(jsonPath("$.userId").isNumber());
    }

    @Test
    void protectedEndpoint_missingToken_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/me"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void protectedEndpoint_malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpoint_forgedSignature_returns401() throws Exception {
        // A token signed with a different secret must not authenticate.
        com.prince.agentic.security.JwtService forger =
                new com.prince.agentic.security.JwtService(
                        "a-totally-different-secret-key-000000000000", 3600, "agentic-ai-task-orchestrator");
        String forged = forger.issueToken(1L, "me@example.com", java.util.List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- authorization (RBAC)

    @Test
    void adminEndpoint_withUserToken_returns403Envelope() throws Exception {
        mockMvc.perform(register("plainuser@example.com", PASSWORD)).andExpect(status().isCreated());
        String userToken = loginToken("plainuser@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/ping"));
    }

    @Test
    void adminEndpoint_withAdminToken_returns200() throws Exception {
        createAdmin("boss@example.com", PASSWORD);   // admin created server-side only
        String adminToken = loginToken("boss@example.com", PASSWORD);

        mockMvc.perform(get("/api/v1/admin/ping").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.scope").value("admin"));
    }

    @Test
    void adminEndpoint_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // ---------------------------------------------------------------- public routes still open under security

    @Test
    void publicRoutes_areReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- actuator exposure remains locked down

    @Test
    void sensitiveActuatorEndpoint_isNotExposed() throws Exception {
        // Anonymous: blocked by security (401). Authenticated: still not exposed (404).
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());

        mockMvc.perform(register("actuator@example.com", PASSWORD)).andExpect(status().isCreated());
        String token = loginToken("actuator@example.com", PASSWORD);
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String email, String password) {
        return post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, password));
    }

    private String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(login(email, password)).andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    private void createAdmin(String email, String rawPassword) {
        Role admin = roleRepository.findByName(RoleNames.ROLE_ADMIN).orElseThrow();
        User user = new User(email.toLowerCase(), passwordEncoder.encode(rawPassword));
        user.addRole(admin);
        userRepository.saveAndFlush(user);
    }
}
