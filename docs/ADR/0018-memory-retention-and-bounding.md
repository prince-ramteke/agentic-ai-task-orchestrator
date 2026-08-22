# ADR-0018 — Memory Retention & Bounding Strategy

**Status:** Accepted · **Milestone:** M7 · **Date:** 2026-08-22

## Context
Conversation memory must never grow unbounded in Redis, must never blow the LLM context window, and
must expire on its own. Redis storage size and LLM context size are different concerns.

## Decision
Two **independent, deterministic** boundaries plus a sliding TTL:

- **Storage bound** — what Redis keeps: `AGENT_MEMORY_MAX_MESSAGES` (50) and `AGENT_MEMORY_MAX_CHARS`
  (12,000). Applied by `MemoryBounds.trimForStorage` before every write (keep newest; always retain
  at least the latest turn).
- **Context bound** — the smaller slice rendered into the prompt: `AGENT_MEMORY_CONTEXT_MAX_MESSAGES`
  (12) and `AGENT_MEMORY_CONTEXT_MAX_CHARS` (6,000), via `MemoryBounds.renderContext`. The full stored
  history is never sent to the model.
- **TTL:** **sliding**, default `AGENT_MEMORY_TTL_SECONDS` = 86,400 (24h), refreshed on every turn's
  write. Active conversations stay alive; idle ones expire within a day.
- **Concurrency:** documented **last-write-wins** — one atomic write per turn from the loaded snapshot.
  A single human awaiting a reply rarely overlaps turns; memory is best-effort ephemeral context, so a
  lost concurrent append is acceptable and is not a correctness violation.
- **Stored content:** `USER` message, `ASSISTANT` final response, and bounded `TOOL` summaries
  (reusing M6's `ObservationSerializer` caps). `SYSTEM` is never stored.

## Consequences
- Predictable Redis footprint and prompt size, independently tunable via env.
- Sliding TTL means a conversation abandoned for 24h is unrecoverable (acceptable for short-term memory;
  durable history is M9 audit, not memory).
