package com.prince.agentic.guardrail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the guardrail layer (M8). Coverage-excluded infrastructure (like {@code config/**}).
 *
 * <p>Enables {@link GuardrailProperties} binding. The engine, policies, rate limiter, and
 * confirmation service are ordinary component-scanned beans; the {@code Clock} they use comes from
 * {@code AgentConfig}. Reuses the auto-configured {@code StringRedisTemplate} (Lettuce) exactly as
 * the M7 memory layer does — no custom template, so confirmation state stays plain application-owned
 * JSON. Mirrors {@code AgentConfig}/{@code MemoryConfig}'s {@code @EnableConfigurationProperties}.
 */
@Configuration
@EnableConfigurationProperties(GuardrailProperties.class)
public class GuardrailConfig {
}
