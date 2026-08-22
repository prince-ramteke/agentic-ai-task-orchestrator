package com.prince.agentic.guardrail;

/**
 * Per-user tool-call rate limiting (spec §7). A single {@code tryAcquire} both checks and consumes
 * one unit of the current window's budget; callers stop with {@code RATE_LIMITED} on {@code false}.
 * Consumption happens only on actual execution — never when an action is merely awaiting confirmation.
 */
public interface RateLimiter {

    /** @return {@code true} if a tool call is within budget (and consumes one), {@code false} otherwise. */
    boolean tryAcquire(long userId);
}
