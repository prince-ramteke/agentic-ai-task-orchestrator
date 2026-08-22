# ADR-0023 — Layered Timeout Strategy

**Status:** Accepted · **Milestone:** M8 · **Date:** 2026-08-22

## Context
M6 enforces a cooperative wall-clock deadline checked between steps but does not hard-interrupt an
in-flight call. M8 was asked to consider hard timeout enforcement. Forcibly cancelling a transactional
domain write (e.g. `Future.cancel(true)` mid-transaction) can leave inconsistent state — it is unsafe.

## Decision
Enforce timeouts in three independent layers, none of which force-cancels a write:

1. **LLM-provider timeout** — the existing `llm.request-timeout-seconds` on the provider call.
2. **Cooperative orchestration deadline** — the existing M6 single deadline, checked between steps.
3. **Per-tool pre-execution budget check** — before invoking a tool, if the deadline is already
   exhausted, fail *before* execution (`AGENT_TIMEOUT`) rather than start work that cannot be safely
   interrupted.

We explicitly **reject** wrapping tool execution in a cancellable `Future` with `cancel(true)` around
`SIDE_EFFECTING` operations. Once a write has started it runs to completion; the system never pretends
an in-flight write can be safely interrupted.

## Consequences
- Honest guarantees: unbounded execution is prevented without risking a torn write.
- The only interruption points are before a call starts or between steps.
- Documented in `docs/GUARDRAILS.md` and `docs/PERFORMANCE.md`.
