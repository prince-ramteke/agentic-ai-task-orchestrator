package com.prince.agentic.tool;

import com.prince.agentic.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Descriptor invariants + the value-type factories (context/result). */
class ToolDescriptorTest {

    private ToolDescriptor valid() {
        return new ToolDescriptor("task.get", "Get one task by id", "task", "1",
                ToolRiskLevel.READ_ONLY, true, Set.of("ROLE_USER"),
                String.class, String.class, Duration.ofSeconds(10));
    }

    @Test
    void descriptor_holds_metadata() {
        ToolDescriptor d = valid();
        assertThat(d.name()).isEqualTo("task.get");
        assertThat(d.risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
        assertThat(d.requiresAuthentication()).isTrue();
    }

    @Test
    void descriptor_rejects_blank_name() {
        assertThatThrownBy(() -> new ToolDescriptor(" ", "d", "c", "1", ToolRiskLevel.READ_ONLY,
                true, Set.of(), String.class, String.class, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptor_rejects_null_risk_and_types() {
        assertThatThrownBy(() -> new ToolDescriptor("n", "d", "c", "1", null,
                true, Set.of(), String.class, String.class, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolDescriptor("n", "d", "c", "1", ToolRiskLevel.READ_ONLY,
                true, Set.of(), null, String.class, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptor_rejects_nonpositive_timeout() {
        assertThatThrownBy(() -> new ToolDescriptor("n", "d", "c", "1", ToolRiskLevel.READ_ONLY,
                true, Set.of(), String.class, String.class, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void context_from_principal_generates_ids_and_keeps_identity() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        assertThat(ctx.principal().userId()).isEqualTo(7L);
        assertThat(ctx.executionId()).isNotBlank();
        assertThat(ctx.requestId()).isNotBlank();
    }

    @Test
    void result_ok_and_failure_factories() {
        ToolResult<String> ok = ToolResult.ok("task.get", "data", 5);
        assertThat(ok.success()).isTrue();
        assertThat(ok.data()).isEqualTo("data");
        ToolResult<String> bad = ToolResult.failure("task.get", new ToolError("TOOL_NOT_FOUND", "no"), 1);
        assertThat(bad.success()).isFalse();
        assertThat(bad.error().code()).isEqualTo("TOOL_NOT_FOUND");
    }
}
