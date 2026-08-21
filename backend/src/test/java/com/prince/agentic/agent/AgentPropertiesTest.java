package com.prince.agentic.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPropertiesTest {

    @Test
    void defaults_areApplied_whenUnset() {
        AgentProperties p = bind(new MockEnvironment());
        assertThat(p.maxIterations()).isEqualTo(8);
        assertThat(p.maxToolCalls()).isEqualTo(10);
        assertThat(p.timeoutSeconds()).isEqualTo(60);
        assertThat(p.loopThreshold()).isEqualTo(2);
        assertThat(p.maxObservationChars()).isEqualTo(2000);
        assertThat(p.maxArrayItems()).isEqualTo(20);
    }

    @Test
    void envOverrides_areBound() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("agent.max-iterations", "3")
                .withProperty("agent.max-tool-calls", "4");
        AgentProperties p = bind(env);
        assertThat(p.maxIterations()).isEqualTo(3);
        assertThat(p.maxToolCalls()).isEqualTo(4);
    }

    private AgentProperties bind(MockEnvironment env) {
        return new Binder(ConfigurationPropertySources.get(env))
                .bindOrCreate("agent", AgentProperties.class);
    }
}
