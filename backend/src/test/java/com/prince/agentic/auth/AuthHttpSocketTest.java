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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real socket-level HTTP verification: boots an actual embedded Tomcat on a random port and
 * calls it over TCP through the full Spring Security filter chain (H2 + real Flyway migrations).
 * Complements {@link AuthIntegrationTest} (MockMvc) by proving wire-level behavior — the packaged
 * app serving JSON over a socket. Not transactional; each test uses a distinct email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthHttpSocketTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "SecurePassword123!";

    @Test
    void fullFlow_registerLoginAccessProtected_overRealHttp() throws Exception {
        // Register
        ResponseEntity<String> register = post("/api/v1/auth/register", json("socket-user@example.com", PASSWORD), null);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Login → token
        ResponseEntity<String> login = post("/api/v1/auth/login", json("socket-user@example.com", PASSWORD), null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = objectMapper.readTree(login.getBody()).get("accessToken").asText();
        assertThat(token).isNotBlank();

        // Protected /me with token → 200
        ResponseEntity<String> me = get("/api/v1/me", token);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(me.getBody()).get("email").asText()).isEqualTo("socket-user@example.com");

        // Protected /me without token → 401
        assertThat(get("/api/v1/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // USER hitting admin → 403
        assertThat(get("/api/v1/admin/ping", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminToken_reachesAdminEndpoint_overRealHttp() throws Exception {
        Role adminRole = roleRepository.findByName(RoleNames.ROLE_ADMIN).orElseThrow();
        User admin = new User("socket-admin@example.com", passwordEncoder.encode(PASSWORD));
        admin.addRole(adminRole);
        userRepository.saveAndFlush(admin);

        String token = objectMapper.readTree(
                post("/api/v1/auth/login", json("socket-admin@example.com", PASSWORD), null).getBody())
                .get("accessToken").asText();

        assertThat(get("/api/v1/admin/ping", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void publicHealth_isReachableWithoutToken_overRealHttp() {
        assertThat(get("/api/v1/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- helpers ---

    private ResponseEntity<String> post(String path, String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
