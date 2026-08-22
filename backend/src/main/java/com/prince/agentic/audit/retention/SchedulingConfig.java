package com.prince.agentic.audit.retention;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Milestone 10 scheduling wiring (ADR-0031). Kept in its own {@code @Configuration} so adding or
 * removing scheduled work in the future is a single-file change and so a test slice can exclude it.
 * Coverage-excluded infrastructure (mirrors {@code AuditConfig}).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class SchedulingConfig {
}
