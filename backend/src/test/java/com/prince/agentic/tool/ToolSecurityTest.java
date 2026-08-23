package com.prince.agentic.tool;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.security.RoleNames;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskCreateRequest;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.user.Role;
import com.prince.agentic.user.RoleRepository;
import com.prince.agentic.user.User;
import com.prince.agentic.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end security proof: the executor + real domain tools enforce role authorization AND, through
 * the domain services, resource ownership (404-masking + admin-any-by-id) — with identity taken only
 * from the backend-built context. Runs on H2 (no Ollama/Docker needed).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ToolSecurityTest {

    @Autowired private ToolExecutor executor;
    @Autowired private ToolRegistry registry;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AuthenticatedUser persistUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User u = new User(email.toLowerCase(), passwordEncoder.encode("SecurePassword123!"));
        u.addRole(role);
        User saved = userRepository.saveAndFlush(u);
        return new AuthenticatedUser(saved.getId(), saved.getEmail(), Set.of(roleName));
    }

    @Test
    void task_get_enforces_ownership_admin_bypass_and_context_identity() {
        AuthenticatedUser userA = persistUser("tool-a@example.com", RoleNames.ROLE_USER);
        AuthenticatedUser userB = persistUser("tool-b@example.com", RoleNames.ROLE_USER);
        AuthenticatedUser admin = persistUser("tool-admin@example.com", RoleNames.ROLE_ADMIN);

        // Arrange: user A creates a task via the domain service (owner = A, server-assigned).
        TaskResponse created = taskService.create(userA,
                new TaskCreateRequest("owned by A", null, null, null, null, null));
        long taskId = created.id();

        // 1. A can read own task via the tool.
        ToolResult<Object> a = executor.execute("task.get", Map.of("taskId", taskId),
                ToolExecutionContext.forPrincipal(userA));
        assertThat(a.success()).isTrue();

        // 2. B cannot — surfaced as NOT_FOUND (404-masking preserved through the tool layer).
        ToolResult<Object> b = executor.execute("task.get", Map.of("taskId", taskId),
                ToolExecutionContext.forPrincipal(userB));
        assertThat(b.success()).isFalse();
        assertThat(b.error().code()).isEqualTo("NOT_FOUND");

        // 3. ADMIN can (admin-any-by-id preserved).
        ToolResult<Object> adm = executor.execute("task.get", Map.of("taskId", taskId),
                ToolExecutionContext.forPrincipal(admin));
        assertThat(adm.success()).isTrue();
    }

    @Test
    void task_create_owner_comes_from_context_not_arguments() {
        AuthenticatedUser userA = persistUser("tool-c@example.com", RoleNames.ROLE_USER);

        // Even if the caller tries to smuggle an ownerId, the input record has no such field →
        // unknown property → TOOL_INVALID_INPUT (loud, not silently ignored).
        ToolResult<Object> spoof = executor.execute("task.create",
                Map.of("title", "x", "priority", "HIGH", "ownerId", 999999),
                ToolExecutionContext.forPrincipal(userA));
        assertThat(spoof.success()).isFalse();
        assertThat(spoof.error().code()).isEqualTo("TOOL_INVALID_INPUT");

        // A clean create succeeds and is owned by A.
        ToolResult<Object> ok = executor.execute("task.create",
                Map.of("title", "via tool", "priority", "HIGH"),
                ToolExecutionContext.forPrincipal(userA));
        assertThat(ok.success()).isTrue();
    }

    @Test
    void anonymous_context_is_unauthorized() {
        ToolExecutionContext anon = new ToolExecutionContext(null, "req", "exec", Map.of(), java.util.Optional.empty());
        ToolResult<Object> r = executor.execute("task.get", Map.of("taskId", 1), anon);
        assertThat(r.error().code()).isEqualTo("TOOL_UNAUTHORIZED");
    }

    @Test
    void destructive_tools_are_not_registered_in_m5() {
        assertThat(registry.contains("task.delete")).isFalse();
        assertThat(registry.contains("task.update")).isFalse();
        assertThat(registry.contains("customer.delete")).isFalse();
    }

    @Test
    void all_six_tools_are_registered() {
        assertThat(registry.size()).isEqualTo(6);
        assertThat(registry.contains("task.get")).isTrue();
        assertThat(registry.contains("task.search")).isTrue();
        assertThat(registry.contains("task.create")).isTrue();
        assertThat(registry.contains("customer.get")).isTrue();
        assertThat(registry.contains("customer.search")).isTrue();
        assertThat(registry.contains("math.calculate")).isTrue();
    }
}
