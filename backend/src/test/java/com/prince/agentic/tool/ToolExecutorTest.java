package com.prince.agentic.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.exception.ResourceNotFoundException;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The ordered-gate pipeline: resolve → auth → bind → validate → execute → wrap. */
class ToolExecutorTest {

    record Echo(@NotNull @Min(1) Long id) {}

    private ToolExecutor executor(Tool<?, ?> tool) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return new ToolExecutor(new ToolRegistry(List.of(tool)), new ObjectMapper(),
                validator, new SimpleMeterRegistry());
    }

    private Tool<Echo, String> echoTool(Set<String> roles, boolean requiresAuth, RuntimeException toThrow) {
        return new Tool<>() {
            @Override
            public ToolDescriptor descriptor() {
                return new ToolDescriptor("echo.do", "echo", "test", "1", ToolRiskLevel.READ_ONLY,
                        requiresAuth, roles, Echo.class, String.class, Duration.ofSeconds(10));
            }
            @Override
            public String execute(ToolExecutionContext c, Echo in) {
                if (toThrow != null) throw toThrow;
                return "id=" + in.id() + " user=" + c.principal().userId();
            }
        };
    }

    private ToolExecutionContext user() {
        return ToolExecutionContext.forPrincipal(new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER")));
    }

    @Test
    void happy_path_binds_validates_executes_and_wraps() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 5), user());
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEqualTo("id=5 user=7");
        assertThat(r.toolName()).isEqualTo("echo.do");
        assertThat(r.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void unknown_tool_is_TOOL_NOT_FOUND() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("missing.tool", Map.of(), user());
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("TOOL_NOT_FOUND");
    }

    @Test
    void anonymous_on_auth_required_tool_is_TOOL_UNAUTHORIZED() {
        ToolExecutionContext anon = new ToolExecutionContext(null, "req", "exec", Map.of(), Optional.empty());
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 5), anon);
        assertThat(r.error().code()).isEqualTo("TOOL_UNAUTHORIZED");
    }

    @Test
    void missing_required_role_is_TOOL_FORBIDDEN() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_ADMIN"), true, null))
                .execute("echo.do", Map.of("id", 5), user());   // user lacks ROLE_ADMIN
        assertThat(r.error().code()).isEqualTo("TOOL_FORBIDDEN");
    }

    @Test
    void invalid_input_is_TOOL_INVALID_INPUT() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 0), user());   // @Min(1)
        assertThat(r.error().code()).isEqualTo("TOOL_INVALID_INPUT");
    }

    @Test
    void unknown_argument_property_is_TOOL_INVALID_INPUT() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 5, "ownerId", 999), user());  // spoofed field
        assertThat(r.error().code()).isEqualTo("TOOL_INVALID_INPUT");
    }

    @Test
    void domain_api_exception_is_surfaced_with_its_code() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true,
                new ResourceNotFoundException("task 5 not found")))   // ApiException, fixed code "NOT_FOUND"
                .execute("echo.do", Map.of("id", 5), user());
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("NOT_FOUND");     // preserved, not TOOL_EXECUTION_FAILED
    }

    @Test
    void unexpected_error_is_TOOL_EXECUTION_FAILED() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, new IllegalStateException("boom")))
                .execute("echo.do", Map.of("id", 5), user());
        assertThat(r.error().code()).isEqualTo("TOOL_EXECUTION_FAILED");
    }
}
