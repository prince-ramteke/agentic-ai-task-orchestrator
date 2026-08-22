package com.prince.agentic.guardrail.exception;

import org.springframework.http.HttpStatus;

/** The per-user tool-call budget for the current window is exhausted (spec §7). {@code 429 RATE_LIMITED}. */
public class RateLimitedException extends GuardrailException {

    public RateLimitedException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Tool-call rate limit exceeded; please retry shortly.");
    }
}
