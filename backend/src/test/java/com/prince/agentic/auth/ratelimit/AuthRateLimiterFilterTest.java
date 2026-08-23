package com.prince.agentic.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-01 unit tests for AuthRateLimiterFilter — mocked Redis, deterministic clock.
 * Integration tests (real Redis) live in AuthRateLimitIT.
 */
class AuthRateLimiterFilterTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-23T10:00:00Z"), ZoneOffset.UTC);

    private StringRedisTemplate redis;
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> ops;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        ops   = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.expire(anyString(), any())).thenReturn(Boolean.TRUE);
    }

    private AuthRateLimiterFilter filter(int loginLimit, int registerLimit, int proxyDepth) {
        AuthRateLimitProperties props = new AuthRateLimitProperties(loginLimit, registerLimit, proxyDepth);
        return new AuthRateLimiterFilter(redis, props, FIXED, mapper);
    }

    // ─── shouldNotFilter ─────────────────────────────────────────────────────

    @Test
    void non_auth_path_is_skipped() throws Exception {
        when(ops.increment(anyString())).thenReturn(1000L); // would reject if checked
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tasks");
        req.setServletPath("/api/v1/tasks");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter(1, 1, 0).doFilter(req, res, chain);
        // Filter skipped → chain was invoked → no 429
        assertThat(res.getStatus()).isEqualTo(200);
    }

    // ─── Login endpoint ──────────────────────────────────────────────────────

    @Test
    void login_within_limit_passes_through() throws Exception {
        when(ops.increment(anyString())).thenReturn(5L); // count=5 ≤ limit=20
        assertThat(doLoginRequest(filter(20, 5, 0))).isEqualTo(200);
    }

    @Test
    void login_at_limit_passes_through() throws Exception {
        when(ops.increment(anyString())).thenReturn(20L); // count==limit
        assertThat(doLoginRequest(filter(20, 5, 0))).isEqualTo(200);
    }

    @Test
    void login_over_limit_is_429() throws Exception {
        when(ops.increment(anyString())).thenReturn(21L); // count > limit=20
        MockHttpServletResponse res = doLoginResponse(filter(20, 5, 0));
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
        assertThat(res.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    // ─── Register endpoint ───────────────────────────────────────────────────

    @Test
    void register_within_limit_passes_through() throws Exception {
        when(ops.increment(anyString())).thenReturn(3L); // count=3 ≤ limit=5
        assertThat(doRegisterRequest(filter(20, 5, 0))).isEqualTo(200);
    }

    @Test
    void register_over_limit_is_429() throws Exception {
        when(ops.increment(anyString())).thenReturn(6L); // count > limit=5
        assertThat(doRegisterRequest(filter(20, 5, 0))).isEqualTo(429);
    }

    // ─── Redis failure ───────────────────────────────────────────────────────

    @Test
    void redis_unavailable_allows_login_with_degradation() throws Exception {
        when(ops.increment(anyString())).thenThrow(new QueryTimeoutException("redis down"));
        assertThat(doLoginRequest(filter(20, 5, 0))).isEqualTo(200);
    }

    // ─── IP extraction ───────────────────────────────────────────────────────

    @Test
    void without_proxy_depth_uses_remote_addr() {
        AuthRateLimiterFilter f = filter(20, 5, 0);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.1");
        req.addHeader("X-Forwarded-For", "10.0.0.1");
        assertThat(f.extractClientIp(req)).isEqualTo("192.168.1.1");
    }

    @Test
    void with_proxy_depth_1_uses_xff_client_ip() {
        AuthRateLimiterFilter f = filter(20, 5, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("proxy.internal");
        // XFF: clientIp, proxyIp → depth=1 → index = hops.length - 1 = 1 → "10.0.0.1"
        req.addHeader("X-Forwarded-For", "10.0.0.1, 172.16.0.1");
        assertThat(f.extractClientIp(req)).isEqualTo("10.0.0.1");
    }

    @Test
    void with_proxy_depth_missing_xff_falls_back_to_remote_addr() {
        AuthRateLimiterFilter f = filter(20, 5, 1);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.0.5");
        assertThat(f.extractClientIp(req)).isEqualTo("192.168.0.5");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private int doLoginRequest(AuthRateLimiterFilter f) throws Exception {
        return doLoginResponse(f).getStatus();
    }

    private MockHttpServletResponse doLoginResponse(AuthRateLimiterFilter f) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", AuthRateLimiterFilter.LOGIN_PATH);
        req.setServletPath(AuthRateLimiterFilter.LOGIN_PATH);
        req.setRemoteAddr("10.10.10.10");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private int doRegisterRequest(AuthRateLimiterFilter f) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", AuthRateLimiterFilter.REGISTER_PATH);
        req.setServletPath(AuthRateLimiterFilter.REGISTER_PATH);
        req.setRemoteAddr("10.10.10.10");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }
}
