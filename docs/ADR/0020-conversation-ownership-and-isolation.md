# ADR-0020 — Conversation Ownership & Isolation

**Status:** Accepted · **Milestone:** M7 · **Date:** 2026-08-22

## Context
A `conversationId` travels in the request body. It must never be usable to reach another user's
memory, and the existence of another user's conversation must not leak.

## Decision
- **Server-minted identifier:** `conversationId` is a server-generated **UUIDv4** — unguessable and
  non-enumerable. Clients never mint ids.
- **Two-layer ownership:** the Redis key is namespaced by the authenticated user's id
  (`conv:{userId}:{conversationId}`), and the stored `ownerUserId` is asserted equal to the principal
  on load (defense-in-depth). Identity always comes from Spring Security (`@AuthenticationPrincipal`),
  never from the body or the id.
- **Existence-masking:** a missing, expired, or non-owned conversation returns a single **404**
  (`CONVERSATION_NOT_FOUND`) — consistent with the M3/M5 ownership convention. A cross-user id resolves
  to the requester's own (empty) key namespace and therefore 404s without touching the owner's data.
- **Memory is untrusted content:** stored text is placed only in the delimited `{history}` prompt slot,
  explicitly marked as context and never as instructions, so it cannot become a replacement system
  prompt (prompt-injection / memory-poisoning defense; deeper guardrails are M8).

## Consequences
- A guessed or manipulated `conversationId` cannot cross users or reveal existence.
- Verified by `RedisConversationMemoryIT` (cross-user 404, user-scoped key) and `AgentConversationIT`
  (User B cannot continue User A's conversation → 404).
