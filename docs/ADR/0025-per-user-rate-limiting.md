# ADR-0025 — Per-User Fixed-Window Rate Limiting

**Status:** Accepted · **Milestone:** M8 · **Date:** 2026-08-22

## Context
M6 bounds tool calls *per execution*. M8 adds a cross-request ceiling so a single user cannot drive
unbounded tool executions over time. We want the simplest mechanism that gives a real ceiling on a
single-node demo, without new infrastructure.

## Decision
- **Per-user fixed window in Redis.** Key `guard:rate:{userId}:{epochMinute}` is `INCR`'d per tool call;
  the first increment sets a short TTL (~2× the window) so windows self-expire. A call is allowed while
  the window count is within `AGENT_USER_TOOL_BUDGET_PER_MIN` (default 60). `epochMinute` derives from
  an injected `Clock`, making window reset deterministic under test.
- **Consumed only on actual execution** — in the orchestrator's `ALLOW` path and in the confirm path —
  never when an action is merely awaiting confirmation.
- **Rejected alternatives:** Bucket4j / distributed token-bucket machinery, and per-conversation limits
  — unjustified complexity for the current scope. Redis `INCR` is atomic, so no distributed lock is
  needed for correctness within a window.

## Consequences
- A real per-user ceiling with one counter; users are isolated by key.
- Over-budget → `429 RATE_LIMITED` (or `BLOCKED` `RATE_LIMITED` inside an agent run).
- Verified by `RedisRateLimiterIT` (below/at/over, window reset, user isolation).
