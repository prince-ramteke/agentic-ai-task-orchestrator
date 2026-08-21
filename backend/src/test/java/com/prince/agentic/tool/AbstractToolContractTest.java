package com.prince.agentic.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The universal contract every {@link Tool} must satisfy. Subclass it per tool and implement
 * {@link #tool()} — so every M5 tool (and every future M6 tool) is guaranteed to be well-formed:
 * a valid dot-namespaced descriptor, typed input/output, a declared auth policy and risk, and a
 * positive timeout.
 */
public abstract class AbstractToolContractTest {

    /** The tool instance under test. */
    protected abstract Tool<?, ?> tool();

    @Test
    void descriptor_is_present_and_well_formed() {
        ToolDescriptor d = tool().descriptor();
        assertThat(d).isNotNull();
        assertThat(d.name()).isNotBlank();
        assertThat(d.name()).as("dot-namespaced, lowercase").matches("[a-z]+\\.[a-z]+");
        assertThat(d.description()).isNotBlank();
        assertThat(d.category()).isNotBlank();
        assertThat(d.version()).isNotBlank();
        assertThat(d.risk()).isNotNull();
        assertThat(d.inputType()).isNotNull();
        assertThat(d.outputType()).isNotNull();
        assertThat(d.timeout()).isNotNull();
        assertThat(d.timeout().isNegative() || d.timeout().isZero()).isFalse();
        assertThat(d.requiredRoles()).isNotNull();
    }

    @Test
    void tool_requires_authentication() {
        assertThat(tool().descriptor().requiresAuthentication())
                .as("all M5 tools require authentication (fail-closed)").isTrue();
    }
}
