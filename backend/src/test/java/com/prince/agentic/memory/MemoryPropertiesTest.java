package com.prince.agentic.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPropertiesTest {

    @Test
    void defaults_areApplied_whenUnset() {
        MemoryProperties p = bind(new MockEnvironment());
        assertThat(p.ttlSeconds()).isEqualTo(86_400);
        assertThat(p.maxMessages()).isEqualTo(50);
        assertThat(p.maxChars()).isEqualTo(12_000);
        assertThat(p.contextMaxMessages()).isEqualTo(12);
        assertThat(p.contextMaxChars()).isEqualTo(6_000);
    }

    @Test
    void envOverrides_areBound() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("agent.memory.ttl-seconds", "3600")
                .withProperty("agent.memory.max-messages", "10")
                .withProperty("agent.memory.context-max-chars", "500");
        MemoryProperties p = bind(env);
        assertThat(p.ttlSeconds()).isEqualTo(3_600);
        assertThat(p.maxMessages()).isEqualTo(10);
        assertThat(p.contextMaxChars()).isEqualTo(500);
    }

    private MemoryProperties bind(MockEnvironment env) {
        return new Binder(ConfigurationPropertySources.get(env))
                .bindOrCreate("agent.memory", MemoryProperties.class);
    }
}
