package com.prince.agentic.guardrail;

import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Real-Redis fixed-window behaviour: below/at/over budget, window reset, and user isolation. */
class RedisRateLimiterIT extends AbstractPostgresIntegrationTest {

    @Autowired private StringRedisTemplate redis;

    private RedisRateLimiter limiter(int budget, Clock clock) {
        return new RedisRateLimiter(redis, new GuardrailProperties(300, budget, 4000),
                clock, new SimpleMeterRegistry());
    }

    /** A controllable clock so the fixed window is deterministic (no wall-clock flakiness). */
    private static final class FixedClock extends Clock {
        private Instant now;
        FixedClock(Instant now) { this.now = now; }
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void belowBudget_allowed_atAndOverBudget_denied() {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-22T10:00:00Z"));
        RateLimiter limiter = limiter(3, clock);
        long user = 5001L;
        assertThat(limiter.tryAcquire(user)).isTrue();   // 1
        assertThat(limiter.tryAcquire(user)).isTrue();   // 2
        assertThat(limiter.tryAcquire(user)).isTrue();   // 3 (== budget)
        assertThat(limiter.tryAcquire(user)).isFalse();  // 4 (over)
        assertThat(limiter.tryAcquire(user)).isFalse();  // 5 (still over)
    }

    @Test
    void newMinute_resetsBudget() {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-22T11:00:00Z"));
        RateLimiter limiter = limiter(2, clock);
        long user = 5002L;
        assertThat(limiter.tryAcquire(user)).isTrue();
        assertThat(limiter.tryAcquire(user)).isTrue();
        assertThat(limiter.tryAcquire(user)).isFalse();
        clock.advanceSeconds(60); // next fixed window → fresh counter key
        assertThat(limiter.tryAcquire(user)).isTrue();
    }

    @Test
    void usersAreIsolated() {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-22T12:00:00Z"));
        RateLimiter limiter = limiter(1, clock);
        assertThat(limiter.tryAcquire(6001L)).isTrue();
        assertThat(limiter.tryAcquire(6001L)).isFalse();
        // A different user has an independent budget in the same window.
        assertThat(limiter.tryAcquire(6002L)).isTrue();
    }
}
