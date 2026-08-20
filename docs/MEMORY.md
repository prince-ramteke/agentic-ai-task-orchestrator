# Memory
## Agentic AI Task Orchestrator

> Conceptual. Redis integration is planned (M7). No memory layer exists yet.

## 1. The two-store rule

| Store | Holds | Lifetime | Losing it means |
|---|---|---|---|
| **PostgreSQL** | Durable application data: users, tasks, customers, **durable agent execution records**, audit events | Permanent | Data loss — unacceptable. |
| **Redis** | Ephemeral operational state: conversation context, session state, in-flight execution state, caches | Short-lived (TTL) | Losing in-flight convenience only — recoverable. |

**Redis is never a replacement for the relational database.** Anything that must survive a restart or be queried later goes in Postgres.

## 2. What lives in Redis

| Data | Key shape (planned) | TTL | Purpose |
|---|---|---|---|
| Conversation context | `conv:{userId}:{conversationId}` | minutes–hours | Short-term dialogue continuity. |
| Session state | `session:{userId}` | session length | Lightweight per-session flags. |
| Execution state | `exec:{executionId}` | run + short grace | Current step, tool-call count, remaining budget, pending confirmation. |
| Cache | `cache:{namespace}:{key}` | explicit per entry | Avoid recomputing stable, expensive results. |

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
