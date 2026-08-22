# ADR-0019 — Redis Failure Semantics

**Status:** Accepted · **Milestone:** M7 · **Date:** 2026-08-22

## Context
Redis can be unavailable. Behavior must be predictable and safe: never leak another user's memory,
never fabricate history, never bypass authentication, and never return a misleading success.

## Decision
A **hybrid** policy that separates the security-critical read from best-effort persistence:

- **Loading an existing conversation is fail-closed.** If Redis is unreachable while loading a
  supplied `conversationId`, the request fails with **503** (`MEMORY_UNAVAILABLE`) *before any tool
  runs* — the ownership gate cannot be verified, so nothing proceeds.
- **A new conversation degrades gracefully.** With no `conversationId`, the conversation is minted
  in-memory (no Redis read). If the post-run persist fails, the turn's result is still returned with
  `memoryStatus=UNAVAILABLE` and `conversationId=null` (stateless degradation).
- **Persistence is best-effort.** For an existing conversation that loaded successfully but whose
  post-run append fails, the already-executed turn is returned with `memoryStatus=UNAVAILABLE` rather
  than a 503 after side effects.

## Consequences
- The 503 case is confined to the pre-execution load, so the agent never runs tools and then fails on
  a memory write.
- Clients get an explicit `memoryStatus` signal instead of silent, undetectable loss of continuity.
- Correctness never depends on a Redis value being present (`docs/MEMORY.md` §5).
