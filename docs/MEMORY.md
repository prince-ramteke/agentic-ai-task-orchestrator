# Memory
## Agentic AI Task Orchestrator

> **Conversation memory is IMPLEMENTED (M7).** Session state, execution state, and caching rows below
> remain PLANNED. Redis is wired via Spring Data Redis (Lettuce); see §7 for the M7 details.

## 1. The two-store rule

| Store | Holds | Lifetime | Losing it means |
|---|---|---|---|
| **PostgreSQL** | Durable application data: users, tasks, customers, **durable agent execution records**, audit events | Permanent | Data loss — unacceptable. |
| **Redis** | Ephemeral operational state: conversation context, session state, in-flight execution state, caches | Short-lived (TTL) | Losing in-flight convenience only — recoverable. |

**Redis is never a replacement for the relational database.** Anything that must survive a restart or be queried later goes in Postgres.

## 2. What lives in Redis

| Data | Key shape | TTL | Purpose | Status |
|---|---|---|---|---|
| Conversation context | `conv:{userId}:{conversationId}` | sliding 24h (`AGENT_MEMORY_TTL_SECONDS`) | Short-term dialogue continuity. | **IMPLEMENTED (M7)** |
| Session state | `session:{userId}` | session length | Lightweight per-session flags. | PLANNED |
| Execution state | `exec:{executionId}` | run + short grace | Current step, tool-call count, remaining budget, pending confirmation. | PLANNED |
| Cache | `cache:{namespace}:{key}` | explicit per entry | Avoid recomputing stable, expensive results. | PLANNED |

Every Redis entry has an explicit TTL. There is no unbounded, never-expiring key.

## 3. What lives in Postgres (memory-adjacent)

- The **durable execution record**: objective, ordered steps taken, tool outcomes, final result — retrievable via `GET /api/agent/executions/{id}` and used for audit and evaluation.
- Audit events (`AUDIT_LOGGING.md`).

The in-flight `exec:{executionId}` state in Redis is the *working memory* of a run; the Postgres execution record is its *permanent history*. When a run completes (or fails), its outcome is persisted to Postgres; the Redis working state is allowed to expire.

## 4. Caching guidance

- Cache only stable, expensive, read-mostly results.
- Every cache entry gets a TTL and a clear invalidation trigger.
- Never cache authorization decisions or per-user sensitive data without careful key scoping and short TTLs.
- Caching is an optimization applied after measurement (`PERFORMANCE.md`), not by default.

## 5. Consistency & safety

- Redis is best-effort; correctness must never depend on a Redis value being present. On a miss, recompute or reload from Postgres.
- Do not store secrets or full sensitive payloads in Redis (`DATA_PRIVACY.md`).
- Redis access is password-protected in non-dev environments (`REDIS_PASSWORD`).

## 6. Testing

Redis-dependent behavior is tested with Testcontainers Redis (`TESTING.md`): TTL expiry, execution-state transitions, cache hit/miss, and the guarantee that no durable data is written only to Redis.

## Milestone 6 — Single-request execution (no Redis yet)

M6 is **single-request, single-execution** orchestration. `AgentExecution` holds run state (ids, deadline, counters, ordered observations) **in memory** for the lifetime of one `POST /api/v1/agent/execute` call; each request starts a fresh `executionId` and state. There is **no** Redis, no cross-request conversation memory, and no persisted execution state. Redis-backed conversation memory is **M7** (below).

## 7. Milestone 7 — Conversation memory (IMPLEMENTED)

Redis-backed, short-lived, per-conversation context that lets a user continue an agent conversation
across requests. Module: `com.prince.agentic.memory` (no Spring AI; tools never touch Redis).

**Model** (`ConversationMemory` → one JSON blob per conversation):

```
ConversationMemory { conversationId, ownerUserId, createdAt, lastActivityAt, schemaVersion=1, messages[] }
MemoryMessage      { role (USER|ASSISTANT|TOOL), content, tool?, sequence, timestamp }
```

**What is stored:** the user message, the assistant's final response, and bounded TOOL summaries
(reusing M6's `ObservationSerializer` caps). **Never** entity graphs, JWTs, security context, raw
`ToolResult` internals, or the SYSTEM prompt (regenerated fresh each request).

**Key & ownership:** `conv:{userId}:{conversationId}` with a server-minted UUIDv4 id. Ownership is
enforced two ways — the userId-scoped key and an asserted stored `ownerUserId` == principal. A missing,
expired, or non-owned conversation returns a masked **404** (ADR-0020).

**Bounds & TTL** (ADR-0018): storage bound 50 msgs / 12,000 chars; a smaller context bound 12 msgs /
6,000 chars is what actually reaches the LLM (the full history is never sent to the model); sliding 24h
TTL refreshed each turn. All env-tunable via `AGENT_MEMORY_*`.

**Integration** (spec §4): `AgentController → AgentConversationService → ConversationMemoryService →
RedisConversationMemoryService`. `AgentConversationService` loads bounded history, runs the Redis-free
M6 `AgentOrchestrator` with it, then appends the bounded turn and refreshes TTL. The orchestrator
receives history as an opaque string in a delimited `{history}` prompt slot.

**Failure semantics** (ADR-0019, hybrid): existing conversation + Redis down at load → **503**
(fail-closed, before any tool runs); new conversation + Redis down → stateless degradation
(`memoryStatus=UNAVAILABLE`, `conversationId=null`); post-run append failure → best-effort, the turn is
still returned as `UNAVAILABLE`.

**Not M7:** durable agent audit (M9), exec/session/cache rows (above), semantic/vector memory, RAG.
