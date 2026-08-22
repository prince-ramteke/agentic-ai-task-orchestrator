# ADR-0017 — Redis Conversation Memory Architecture

**Status:** Accepted · **Milestone:** M7 · **Date:** 2026-08-22

## Context
M6 executes stateless single-request agent runs. M7 adds short-term, request-to-request conversation
continuity so a user can follow up on a prior turn. `docs/MEMORY.md` already reserved Redis for
ephemeral conversation state under a userId-scoped key, with PostgreSQL remaining the source of truth.

## Decision
Store each conversation as a **single application-owned JSON blob** in Redis under
`conv:{userId}:{conversationId}`, served through a `ConversationMemoryService` abstraction
(`RedisConversationMemoryService` impl, Spring Data Redis + Lettuce, `StringRedisTemplate`).

- **Representation:** one JSON string per conversation (`ConversationMemory` record with a `messages`
  list). No Java native serialization, no class-name polymorphic storage, no entity graphs / tokens /
  security context. A `schemaVersion` field is reserved for future migrations.
- **Module boundary:** a dedicated `com.prince.agentic.memory` package with no Spring AI dependency;
  tools never access Redis. The M6 `AgentOrchestrator` stays Redis-free and receives an already-
  rendered, already-bounded history string (see ADR-0015 orchestration boundary).
- **Data structure choice:** a blob over Redis List/Hash/Stream because the object is small and
  bounded; a blob makes atomic read/write, trimming, per-turn TTL refresh, and versioning trivial.

## Consequences
- Simple, testable, and faithful to the two-store rule (`docs/MEMORY.md`): losing Redis loses only
  convenience, never durable data.
- A whole-conversation read/write per turn (acceptable at these bounds). Concurrency is last-write-wins
  (ADR-0018); a future optimistic-CAS upgrade is enabled by `schemaVersion`/atomic writes without a
  storage-model change.
