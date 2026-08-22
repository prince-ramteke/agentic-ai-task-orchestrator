package com.prince.agentic.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the durable audit layer (M9). Coverage-excluded infrastructure (like {@code config/**}).
 * Enables {@link AuditProperties}; entities/repositories/services are component-scanned. Mirrors
 * {@code AgentConfig}/{@code MemoryConfig}/{@code GuardrailConfig}.
 */
@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {
}
