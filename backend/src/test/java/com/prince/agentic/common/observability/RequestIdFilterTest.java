package com.prince.agentic.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the M10 request-correlation contract (ADR-0030): a supplied UUID is echoed, any other
 * value is discarded and replaced by a fresh UUID, MDC is populated for the downstream chain, and
 * MDC is cleared even when the downstream filter throws.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void resolve_validUuid_isEchoed() {
        String id = UUID.randomUUID().toString();
        assertThat(RequestIdFilter.resolve(id)).isEqualTo(id);
    }

    @Test
    void resolve_nullOrBlank_mintsFresh() {
        String a = RequestIdFilter.resolve(null);
        String b = RequestIdFilter.resolve("   ");
        UUID.fromString(a);   // parses
        UUID.fromString(b);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void resolve_junk_mintsFresh() {
        String out = RequestIdFilter.resolve("not-a-uuid; DROP TABLE users;");
        UUID.fromString(out); // parses as UUID → filter did NOT trust the junk
    }

    @Test
    void doFilter_missingHeader_mintsAndSetsResponseHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();
        FilterChain chain = (r, s) -> observed.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(req, resp, chain);

        String header = resp.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotBlank();
        UUID.fromString(header);
        assertThat(observed.get()).isEqualTo(header);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull(); // cleared after
    }

    @Test
    void doFilter_validHeader_isEchoedExactly() throws Exception {
        String supplied = UUID.randomUUID().toString();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestIdFilter.HEADER, supplied);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        assertThat(resp.getHeader(RequestIdFilter.HEADER)).isEqualTo(supplied);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilter_junkHeader_isReplaced() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestIdFilter.HEADER, "attacker\r\ninjected");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (r, s) -> {});

        String header = resp.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotEqualTo("attacker\r\ninjected");
        UUID.fromString(header);
    }

    @Test
    void doFilter_downstreamThrows_stillClearsMdc() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain boom = (r, s) -> { throw new ServletException("boom"); };

        assertThatThrownBy(() -> filter.doFilter(req, resp, boom))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

}
