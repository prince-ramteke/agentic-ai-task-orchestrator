package com.prince.agentic.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Milestone 10 request-correlation filter (ADR-0030).
 *
 * <p>Reads the {@value #HEADER} request header — accepted only when it is a well-formed UUID; any
 * other value (missing, junk, an attempted path/injection payload) is discarded and a fresh UUIDv4
 * is minted. The chosen id is placed into SLF4J {@link MDC} under the key {@value #MDC_KEY} for the
 * duration of the request, echoed back on the response as the same header, and <b>cleared in
 * {@code finally}</b> so it never leaks across pool-reused threads.
 *
 * <p>Never accepts arbitrary strings: this is defense-in-depth against a client seeding the MDC/log
 * pipeline with attacker-controlled content (docs/DATA_PRIVACY.md).
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Return the header value only if it parses as a UUID; otherwise mint a fresh UUIDv4. */
    static String resolve(String supplied) {
        if (supplied != null && !supplied.isBlank()) {
            try {
                return UUID.fromString(supplied.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // fall through to mint
            }
        }
        return UUID.randomUUID().toString();
    }
}
