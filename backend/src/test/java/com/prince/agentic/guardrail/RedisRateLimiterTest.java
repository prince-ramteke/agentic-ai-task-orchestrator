package com.prince.agentic.guardrail;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RedisRateLimiter — mocked Redis, deterministic clock.
 * Integration tests (real Redis) live in RedisRateLimiterIT.
 */
class RedisRateLimiterTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-23T09:00:00Z"), ZoneOffset.UTC);
    private static final GuardrailProperties PROPS = new GuardrailProperties(300, 5, 4000);

    @SuppressWarnings("unchecked")
    private RedisRateLimiter limiterWithRedis(StringRedisTemplate redis) {
        return new RedisRateLimiter(redis, PROPS, FIXED, new SimpleMeterRegistry());
    }

    @Test
    void redis_unavailable_allows_with_degradation() {
        // M-01: when Redis throws DataAccessException, tryAcquire must return true (allow-with-degradation).
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new QueryTimeoutException("simulated Redis timeout"));

        RedisRateLimiter limiter = limiterWithRedis(redis);
        assertThat(limiter.tryAcquire(42L)).isTrue();
    }

    @Test
    void within_budget_returns_true() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(3L); // count=3, budget=5
        when(redis.expire(anyString(), any())).thenReturn(Boolean.TRUE);

        assertThat(limiterWithRedis(redis).tryAcquire(1L)).isTrue();
    }

    @Test
    void at_budget_returns_true() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(5L); // count==budget

        assertThat(limiterWithRedis(redis).tryAcquire(1L)).isTrue();
    }

    @Test
    void over_budget_returns_false() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(6L); // count > budget=5

        assertThat(limiterWithRedis(redis).tryAcquire(1L)).isFalse();
    }
}
