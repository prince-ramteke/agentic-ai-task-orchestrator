package com.prince.agentic.guardrail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Zero/unset values fall back to the documented defaults so a minimal environment binds cleanly. */
class GuardrailPropertiesTest {

    @Test
    void zeroValues_fallBackToDefaults() {
        GuardrailProperties p = new GuardrailProperties(0, 0, 0);
        assertThat(p.confirmationTtlSeconds()).isEqualTo(300);
        assertThat(p.userToolBudgetPerMin()).isEqualTo(60);
        assertThat(p.maxArgumentChars()).isEqualTo(4000);
    }

    @Test
    void explicitValues_areKept() {
        GuardrailProperties p = new GuardrailProperties(120, 5, 1000);
        assertThat(p.confirmationTtlSeconds()).isEqualTo(120);
        assertThat(p.userToolBudgetPerMin()).isEqualTo(5);
        assertThat(p.maxArgumentChars()).isEqualTo(1000);
    }
}
