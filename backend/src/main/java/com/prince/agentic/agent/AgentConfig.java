package com.prince.agentic.agent;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the agent layer. Coverage-excluded infrastructure (like {@code config/**}).
 *
 * <p>Self-contained: enables {@link AgentProperties} binding and provides the {@link Clock} the
 * orchestrator uses to compute a deterministic wall-clock deadline (spec: inject {@code Clock},
 * bean is {@code Clock.systemUTC()}, so deadlines are testable). Mirrors
 * {@code com.prince.agentic.ai.config.AiConfig}'s {@code @EnableConfigurationProperties} pattern;
 * deliberately does not touch {@code AgenticApplication}.
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
