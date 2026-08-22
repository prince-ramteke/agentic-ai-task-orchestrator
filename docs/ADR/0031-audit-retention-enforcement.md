# ADR-0031 — Audit Retention Enforcement (Scheduled, Batched, Best-Effort)

**Status:** Accepted · **Milestone:** M10 · **Date:** 2026-08-22

## Context
M9 shipped a durable agent-audit model (three typed tables) and documented a retention horizon via
`AGENT_AUDIT_RETENTION_DAYS` (default 90 days), but **did not enforce it** — rows accumulated
forever. M10 must actually purge expired rows without destabilising the agent path, without a naïve
unbounded `DELETE`, and without any new infrastructure. This is a single-node application; a
distributed lock is out of scope.

## Decision

### D7 — Scheduled, env-tunable, disabled in tests
A `SchedulingConfig` enables Spring `@Scheduled`. `AuditRetentionJob.scheduled()` runs on
`audit.purge.cron` (default `0 15 3 * * *`, **UTC**) — nightly at 03:15 UTC. All timing is
env-overrideable:

| Env var | Default |
|---|---|
| `AGENT_AUDIT_PURGE_ENABLED` | `true` |
| `AGENT_AUDIT_PURGE_CRON` | `0 15 3 * * *` |
| `AGENT_AUDIT_PURGE_BATCH_SIZE` | `500` |
| `AGENT_AUDIT_PURGE_MAX_BATCHES` | `100` |

The `test` profile disables the scheduler (`audit.purge.enabled: false`) so surefire runs are
deterministic. The `it` profile keeps the job **enabled** (matches production); the nightly cron
never fires during a short test run, and `AuditRetentionIT` invokes `runOnce()` directly.

### D8 — Parent-first purge using `started_at`; children cascade via existing FKs
Retention deletes only `agent_executions` rows where `started_at < now(UTC) - retentionDays`.
Children (`agent_steps`, `tool_executions`) are removed by the existing `ON DELETE CASCADE`
foreign keys declared in `V5__create_agent_audit.sql` — no new migration, no child-first pass, no
join in the DELETE.

`started_at` is chosen deliberately over `completed_at` and `created_at`:
- `completed_at` is nullable — a crashed / stuck run would never become eligible. Retention would
  strand exactly the rows most in need of pruning.
- `started_at` is `NOT NULL`, matches the semantic "when this happened", and is already indexed via
  `idx_agent_exec_owner_started` — the ORDER BY hits an index.

The delete SQL is portable across PostgreSQL and H2 PG-mode:
```sql
DELETE FROM agent_executions
 WHERE id IN (
   SELECT id FROM agent_executions
    WHERE started_at < :cutoff
    ORDER BY started_at
    LIMIT :batch
 )
```
Each batch is its own short `@Transactional` unit — writers are not blocked for long, a crash
mid-loop leaves committed batches intact, and the SQL predicate is strict `<` so rows equal to the
cutoff survive.

### D9 — Best-effort failure semantics
If a batch throws:
1. WARN log `retention.purge.batch_failed table=… error=…` (exception class name only, no PII).
2. `retention.purge.failure{table="agent_executions"}` increments.
3. The loop short-circuits for this run (do not hammer a struggling DB).
4. **Never rethrown** — the scheduler must not report the application unhealthy.

The next scheduled tick retries.

### D10 — Overlap protection via in-process lock
`ReentrantLock.tryLock()` at job entry. If the lock is not free, INFO
`retention.purge.skipped_overlap` and return — a normal, expected event, not counted as a failure.
Multi-node coordination (e.g. `pg_try_advisory_lock`) is deferred; the application is single-node
today. The limitation is documented in the spec (§12).

### D14 — Retention metrics
- `retention.purge.started{table}` — one increment per invocation.
- `retention.purge.deleted{table}` — incremented per batch by the exact `rowsDeleted`.
- `retention.purge.failure{table}` — one increment per failed batch.
- `retention.purge.duration{table}` — Timer wrapping the whole invocation.

The single tag `table` is bounded (one value: `agent_executions`). No user/execution IDs.

## Consequences
- `agent_executions` no longer grows without bound; retention is enforced consistently with the
  documented `AGENT_AUDIT_RETENTION_DAYS`.
- The purge runs at most `batch_size * max_batches` rows per invocation (default 50 000). A larger
  backlog naturally spreads across nights; no single run can lock the DB for an unbounded stretch.
- Audit writes are unaffected (M9 `REQUIRES_NEW` semantics preserved).
- Metrics are low-cardinality and dashboard-ready without any renames.

## Alternatives considered
- **Single unbounded `DELETE WHERE started_at < cutoff`.** Rejected — long transaction blocks
  writers; on a struggling DB no partial progress survives.
- **Cascade-delete children explicitly first.** Rejected — the schema already declares
  `ON DELETE CASCADE`; a manual child pass duplicates work and risks divergence.
- **Delete rows one-by-one.** Rejected — 500× the round-trips for no benefit.
- **Distributed advisory lock (`pg_try_advisory_lock`).** Deferred — single-node deployment does not
  need it; add via a future ADR when the topology changes.

## Verification
- `AuditRetentionJobTest` — disabled flag, batch loop, `maxBatches` cap, batch-failure best-effort,
  `ReentrantLock` overlap skip.
- `AuditRetentionPropertiesTest` — defaults + normalization.
- `AuditRetentionIT` (Testcontainers Postgres) — end-to-end: 3 old + 2 fresh rows with children;
  after `runOnce()`, only fresh remain, cascaded children of purged parents are gone, fresh
  parents' children survive, `retention.purge.deleted` incremented by exactly 3, second call is a
  no-op.

`./mvnw verify` — 383 unit + 44 integration tests, 3 skipped (live Ollama, unrelated), coverage
gate held (92.71% instruction, well above the 75% minimum).
