package com.prince.agentic.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.support.AbstractPostgresIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-01 integration tests: real Redis via Testcontainers (AbstractPostgresIntegrationTest).
 * Verifies counter accumulation, limit enforcement, window reset, and user isolation.
 */
class AuthRateLimitIT extends AbstractPostgresIntegrationTest {

    @Autowired private StringRedisTemplate redis;

    private static final class FixedClock extends Clock {
        private Instant now;
        FixedClock(Instant now) { this.now = now; }
        void advanceSeconds(long s) { now = now.plusSeconds(s); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private AuthRateLimiterFilter filter(int loginLimit, int registerLimit, FixedClock clock) {
        AuthRateLimitProperties props = new AuthRateLimitProperties(loginLimit, registerLimit, 0);
        return new AuthRateLimiterFilter(redis, props, clock, new ObjectMapper().findAndRegisterModules());
    }

    private int login(AuthRateLimiterFilter f, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", AuthRateLimiterFilter.LOGIN_PATH);
        req.setServletPath(AuthRateLimiterFilter.LOGIN_PATH);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void below_limit_allowed_at_and_over_limit_denied() throws Exception {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-23T09:30:00Z"));
        AuthRateLimiterFilter f = filter(3, 5, clock);
        String ip = "192.0.2.1";
        assertThat(login(f, ip)).isEqualTo(200); // 1
        assertThat(login(f, ip)).isEqualTo(200); // 2
        assertThat(login(f, ip)).isEqualTo(200); // 3 == limit
        assertThat(login(f, ip)).isEqualTo(429); // 4 > limit
        assertThat(login(f, ip)).isEqualTo(429); // 5 still over
    }

    @Test
    void new_window_resets_counter() throws Exception {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-23T09:31:00Z"));
        AuthRateLimiterFilter f = filter(2, 5, clock);
        String ip = "192.0.2.2";
        assertThat(login(f, ip)).isEqualTo(200);
        assertThat(login(f, ip)).isEqualTo(200);
        assertThat(login(f, ip)).isEqualTo(429);
        clock.advanceSeconds(60); // new minute → fresh key
        assertThat(login(f, ip)).isEqualTo(200);
    }

    @Test
    void different_ips_are_isolated() throws Exception {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-23T09:32:00Z"));
        AuthRateLimiterFilter f = filter(1, 5, clock);
        assertThat(login(f, "192.0.2.10")).isEqualTo(200);
        assertThat(login(f, "192.0.2.10")).isEqualTo(429);
        // Different IP has independent counter
        assertThat(login(f, "192.0.2.11")).isEqualTo(200);
    }

    @Test
    void retry_after_header_present_on_429() throws Exception {
        FixedClock clock = new FixedClock(Instant.parse("2026-08-23T09:33:00Z"));
        AuthRateLimiterFilter f = filter(1, 5, clock);
        String ip = "192.0.2.20";
        login(f, ip); // exhaust limit
        MockHttpServletRequest req = new MockHttpServletRequest("POST", AuthRateLimiterFilter.LOGIN_PATH);
        req.setServletPath(AuthRateLimiterFilter.LOGIN_PATH);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    }
}
