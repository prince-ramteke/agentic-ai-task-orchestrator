package com.prince.agentic.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/**
 * H-01: wiring for the IP-based auth rate limiter.
 *
 * <p>Enables {@link AuthRateLimitProperties} binding and exposes the filter as a bean.
 * The filter is registered in the security filter chain by {@code SecurityConfig} before
 * {@code UsernamePasswordAuthenticationFilter}.
 *
 * <p>Coverage-excluded infrastructure (like other {@code config/**} classes).
 */
@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfig {

    @Bean
    public AuthRateLimiterFilter authRateLimiterFilter(StringRedisTemplate redis,
                                                       AuthRateLimitProperties props,
                                                       Clock clock,
                                                       ObjectMapper objectMapper) {
        return new AuthRateLimiterFilter(redis, props, clock, objectMapper);
    }
}
