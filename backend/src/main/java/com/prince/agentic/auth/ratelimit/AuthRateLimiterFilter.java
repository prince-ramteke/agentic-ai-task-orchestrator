package com.prince.agentic.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * H-01: IP-keyed, per-minute rate limiter for the pre-authentication auth endpoints.
 *
 * <p>Applied only to {@code /api/v1/auth/login} and {@code /api/v1/auth/register} before any
 * authentication filter runs. Uses Redis key {@code auth:rate:{ip}:{epochMinute}} with limits:
 * <ul>
 *   <li>Login: {@link AuthRateLimitProperties#loginLimitPerMin()} (default 20)</li>
 *   <li>Register: {@link AuthRateLimitProperties#registerLimitPerMin()} (default 5)</li>
 * </ul>
 *
 * <p><b>Trusted-proxy handling:</b> the client IP is extracted from {@code X-Forwarded-For} only
 * when {@link AuthRateLimitProperties#trustedProxyDepth()} {@code > 0}; otherwise the TCP remote
 * address is used. This prevents IP-spoofing attacks via header manipulation when the app is not
 * behind a known proxy.
 *
 * <p><b>Redis failure:</b> if Redis is unavailable, the filter allows the request (allow-with-
 * degradation, matching the {@code RedisRateLimiter} policy). A WARN is logged (see docs/SECURITY.md).
 */
public class AuthRateLimiterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiterFilter.class);
    private static final String KEY_PREFIX = "auth:rate:";

    static final String LOGIN_PATH    = "/api/v1/auth/login";
    static final String REGISTER_PATH = "/api/v1/auth/register";

    private final StringRedisTemplate redis;
    private final AuthRateLimitProperties props;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public AuthRateLimiterFilter(StringRedisTemplate redis,
                                 AuthRateLimitProperties props,
                                 Clock clock,
                                 ObjectMapper objectMapper) {
        this.redis = redis;
        this.props = props;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !LOGIN_PATH.equals(path) && !REGISTER_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path  = request.getServletPath();
        int    limit = LOGIN_PATH.equals(path) ? props.loginLimitPerMin() : props.registerLimitPerMin();
        String ip    = extractClientIp(request);

        long epochMinute = clock.instant().getEpochSecond() / 60;
        String key = KEY_PREFIX + ip + ":" + epochMinute;

        boolean allowed = tryAcquire(key, limit, ip, epochMinute);
        if (allowed) {
            chain.doFilter(request, response);
        } else {
            writeRateLimitResponse(response, path, ip);
        }
    }

    private boolean tryAcquire(String key, int limit, String ip, long epochMinute) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(120)); // ~2× window; self-expiring
            }
            boolean allowed = count != null && count <= limit;
            if (!allowed) {
                log.warn("auth.rate_limited ip={} window={} count={} limit={}", ip, epochMinute, count, limit);
            }
            return allowed;
        } catch (DataAccessException e) {
            // Redis unavailable — allow-with-degradation (same policy as RedisRateLimiter, docs/SECURITY.md).
            log.warn("auth.rate_limiter redis_unavailable ip={} window={} — allowing (degraded)", ip, epochMinute, e);
            return true;
        }
    }

    /**
     * Extracts the effective client IP address.
     *
     * <p>When {@code trustedProxyDepth > 0}, the {@code X-Forwarded-For} header is consulted:
     * the real client IP is the entry at index {@code (total_hops - trustedProxyDepth)} from the
     * left, since each trusted proxy appends its own address. Falls back to the TCP remote address
     * when the header is absent or depth is zero.
     */
    String extractClientIp(HttpServletRequest request) {
        if (props.trustedProxyDepth() > 0) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] hops = xff.split(",");
                // XFF entries are appended left-to-right; the rightmost N are trusted proxy-added.
                // The real client IP is just to the left of those N trusted entries.
                // index = max(0, hops.length - trustedProxyDepth - 1), clamped at 0.
                int clientIndex = Math.max(0, hops.length - props.trustedProxyDepth() - 1);
                return hops[clientIndex].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, String path, String ip)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later.",
                path,
                null,
                null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
