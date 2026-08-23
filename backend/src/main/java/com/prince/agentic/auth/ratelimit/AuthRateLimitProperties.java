package com.prince.agentic.auth.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * H-01: configurable per-IP auth endpoint rate limits.
 *
 * <p>Separate namespace from the agent guardrail rate limiter ({@code guard:rate:*}):
 * auth endpoints are pre-authentication, so they are keyed by client IP, not userId.
 * Redis key: {@code auth:rate:{ip}:{epochMinute}}.
 *
 * @param loginLimitPerMin    maximum login attempts per IP per minute (default 20)
 * @param registerLimitPerMin maximum register attempts per IP per minute (default 5)
 * @param trustedProxyDepth   number of trusted X-Forwarded-For hops; 0 = trust remote address only
 */
@Validated
@ConfigurationProperties("auth.rate-limit")
public record AuthRateLimitProperties(
        int loginLimitPerMin,
        int registerLimitPerMin,
        int trustedProxyDepth) {

    public AuthRateLimitProperties {
        if (loginLimitPerMin <= 0)    loginLimitPerMin    = 20;
        if (registerLimitPerMin <= 0) registerLimitPerMin = 5;
        if (trustedProxyDepth < 0)    trustedProxyDepth   = 0;
    }
}
