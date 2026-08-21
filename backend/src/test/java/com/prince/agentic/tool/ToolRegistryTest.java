package com.prince.agentic.tool;

import com.prince.agentic.tool.exception.ToolRegistrationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private Tool<String, String> fake(String name, Set<String> roles) {
        return new Tool<>() {
            @Override
            public ToolDescriptor descriptor() {
                return new ToolDescriptor(name, "d", "c", "1", ToolRiskLevel.READ_ONLY, true,
                        roles, String.class, String.class, Duration.ofSeconds(10));
            }
            @Override
            public String execute(ToolExecutionContext c, String in) {
                return in;
            }
        };
    }

    private Tool<String, String> fake(String name) {
        return fake(name, Set.of("ROLE_USER"));
    }

    @Test
    void registers_and_resolves_by_name() {
        ToolRegistry reg = new ToolRegistry(List.of(fake("task.get"), fake("task.search")));
        assertThat(reg.contains("task.get")).isTrue();
        assertThat(reg.resolve("task.search")).isNotNull();
        assertThat(reg.resolve("nope")).isNull();
        assertThat(reg.size()).isEqualTo(2);
    }

    @Test
    void duplicate_name_fails_fast() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(fake("task.get"), fake("task.get"))))
                .isInstanceOf(ToolRegistrationException.class);
    }

    @Test
    void invalid_role_fails_fast() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(fake("task.get", Set.of("USER")))))
                .isInstanceOf(ToolRegistrationException.class);
    }

    @Test
    void descriptors_view_is_immutable_and_sorted() {
        ToolRegistry reg = new ToolRegistry(List.of(fake("task.search"), fake("task.get")));
        List<ToolDescriptor> d = reg.descriptors();
        assertThat(d).extracting(ToolDescriptor::name).containsExactly("task.get", "task.search");
        assertThatThrownBy(() -> d.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
