package com.prince.agentic.memory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the conversation-memory layer. Coverage-excluded infrastructure (like {@code config/**}).
 *
 * <p>Enables {@link MemoryProperties} binding. The Redis connection factory and
 * {@code StringRedisTemplate} come from Spring Boot's {@code spring-boot-starter-data-redis}
 * auto-configuration (Lettuce), driven by {@code spring.data.redis.*}; this module deliberately
 * adds no custom template so serialization stays a plain application-owned JSON string
 * ({@code RedisConversationMemoryService}), never Java native or class-name polymorphic storage.
 * Mirrors {@code AgentConfig}'s {@code @EnableConfigurationProperties} pattern.
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {
}
