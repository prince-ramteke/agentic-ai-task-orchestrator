package com.prince.agentic.guardrail;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * Redis per-user fixed-window rate limiter (spec §7, §30). Key {@code guard:rate:{userId}:{epochMinute}}
 * is {@code INCR}'d per tool call; the first increment sets a short TTL (≈2× the window) so windows
 * self-expire and never accumulate. A call is allowed while the window count is within
 * {@code guardrail.user-tool-budget-per-min}. No Bucket4j, no distributed token bucket, no
 * per-conversation limit — a single deterministic counter keyed by an injected {@link Clock}.
 *
 * <p>Users are isolated by key. Redis {@code INCR} is atomic, so concurrent calls in the same window
 * cannot exceed the budget.
 */
@Service
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "guard:rate:";

    private final StringRedisTemplate redis;
    private final GuardrailProperties props;
    private final Clock clock;
    private final MeterRegistry meters;

    public RedisRateLimiter(StringRedisTemplate redis, GuardrailProperties props,
                            Clock clock, MeterRegistry meters) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    public boolean tryAcquire(long userId) {
        long epochMinute = clock.instant().getEpochSecond() / 60;
        String key = KEY_PREFIX + userId + ":" + epochMinute;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First hit in this window → bound its lifetime (~2× window) so stale windows self-expire.
            redis.expire(key, Duration.ofSeconds(120));
        }
        boolean allowed = count != null && count <= props.userToolBudgetPerMin();
        if (!allowed) {
            meters.counter("guardrail.rate_limited").increment();
            log.info("guardrail.rate_limited user={} window={} count={} budget={}",
                    userId, epochMinute, count, props.userToolBudgetPerMin());
        }
        return allowed;
    }
}
