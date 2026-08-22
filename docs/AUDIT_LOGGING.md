# Audit Logging
## Agentic AI Task Orchestrator

> Conceptual. Auditing is planned (M9). No audit trail exists yet. Audit is durable domain data in PostgreSQL, distinct from operational logs.

## 1. Audit vs. logs

- **Operational logs** (`OBSERVABILITY.md`) are for debugging and metrics; they may be sampled/rotated.
- **Audit records** are a durable, tamper-evident account of *who did what, when, and whether it was allowed* — persisted in Postgres and retrievable. They must not be dropped.

## 2. Events that MUST be audited

- Authentication events (login success/failure).
- Authorization failures (denied access, denied tool invocation).
- Agent execution start/completion/failure/cancellation.
- Each tool **selection** (what the agent proposed).
- Each tool **execution** (and its result/error).
- Every **side effect** (create/update/delete/external send).
- **Confirmation** requests and their outcomes.
- Every high-risk operation.
- Guardrail trips (budget exhausted, loop detected, rate limited).

## 3. Required fields per audit event

| Field | Meaning |
|---|---|
| `who` | Authenticated principal (user id / role). |
| `what` | Action/event type. |
| `when` | `TIMESTAMPTZ`. |
| `tool` | Tool name (for tool events). |
| `resource` | Target resource type + id (for resource actions). |
| `result` | Outcome: allowed/denied, success/failure, confirmed/rejected. |
| `executionId` | Agent execution the event belongs to. |
| `correlationId` | Request correlation id, to tie back to logs/metrics. |
| `reason` | Short reason on denial/failure/guardrail trip. |

## 4. What must NEVER appear in audit records

Secrets, tokens, passwords, JWTs, raw prompts, or full sensitive payloads. Store references/ids and redacted summaries, not sensitive content (`DATA_PRIVACY.md`).

## 5. Retrieval & access

- `GET /api/agent/executions/{id}` returns a user's own execution with its ordered, audited steps.
- Admin endpoints allow inspection across users (RBAC-gated).
- Audit queries are paginated and indexed on `executionId`, `who`, and `when`.

## 6. Integrity

Audit records are append-only in application semantics (no update/delete via the API). Consider write-once discipline and, later, integrity checks if the threat model warrants it (ADR).

## 7. Testing

Assert that: every side-effecting tool call produces an audit record with the required fields; authorization denials are audited; no secret/PII appears in any audit record; admin retrieval is RBAC-gated (`TESTING.md`).

## Milestone 6 — Agent logging (durable audit still M9)

M6 emits structured SLF4J logs and Micrometer metrics keyed by `executionId`/`requestId` (decision action, chosen tool name, status, iterations, tool calls, duration) — never full prompts, arguments, or observations. There are **no** durable audit tables yet: `agent_executions`, `agent_steps`, and `tool_executions`, plus the `GET /api/v1/agent/executions/{id}` retrieval endpoint, are **M9**.

## Milestone 7 — Memory is not audit

Redis conversation **memory** (M7) is short-lived context for future model reasoning; it expires by TTL
and is best-effort. Durable agent **audit** — a permanent, queryable record of what actually happened —
is the separate **M9 (PLANNED)** concern backed by PostgreSQL. Do not treat memory as audit: memory may
vanish on expiry or a Redis outage without any correctness impact.

## Milestone 8 — Logging now; durable audit still M9 (PLANNED)

M8 emits structured guardrail logs (decision, reasonCode, policyId, execution/request ids; arguments
redacted) and low-cardinality guardrail metrics, and keeps short-lived confirmation/rate state in Redis
with TTLs. M8 deliberately creates **no durable audit tables** (`agent_executions`, `tool_executions`,
`agent_steps`) — persistent, queryable audit records remain **M9 (PLANNED)**.

## Milestone 9 — Durable agent audit (IMPLEMENTED)

M9 makes this real. Three typed tables (`agent_executions`, `agent_steps`, `tool_executions` — **no**
generic `audit_events` table) durably record backend-observed facts: execution lifecycle, LLM-decision
metadata, guardrail outcomes, confirmation required/approved, and tool execution outcomes — written via
a repository-free `AgentExecutionListener` seam, never by the LLM, never reconstructed from prompts.

**Supersedes §5 (admin retrieval):** M9 ships **owner-scoped only** — `GET /api/v1/agent/executions`
and `/{executionId}` return the caller's own executions (USER and ADMIN alike); a foreign/missing id is
masked 404. An explicit RBAC-gated admin cross-user endpoint is a documented **later** decision (no
cross-user data path ships in M9).

**Writes** are best-effort in their own `REQUIRES_NEW` transactions, idempotent (UNIQUE natural keys),
and never block or roll back the agent/domain path — a business action can succeed while its audit row
is temporarily missing (recorded as `audit.write.failure`). **Never stored:** raw prompts, tool
arguments, LLM output, system prompts, chain-of-thought, or secrets — only metadata, `arguments_hash`
(SHA-256), and bounded summaries. See ADR-0026…0029.

## Milestone 10 — Retention enforcement (IMPLEMENTED, supersedes "no purge scheduler in M9")

`AGENT_AUDIT_RETENTION_DAYS` (default 90) is now **enforced** by the `AuditRetentionJob` (ADR-0031).
The scheduler:
- runs on `AGENT_AUDIT_PURGE_CRON` (default `0 15 3 * * *`, UTC);
- deletes `agent_executions` rows where `started_at < now - retentionDays` (strictly less-than —
  fresh rows always survive);
- batches of `AGENT_AUDIT_PURGE_BATCH_SIZE` (default 500), capped at
  `AGENT_AUDIT_PURGE_MAX_BATCHES` (default 100) per invocation → ceiling 50 000 rows/run;
- relies on the existing `ON DELETE CASCADE` FKs so `agent_steps` and `tool_executions` are removed
  transactionally with their parent (no separate child-delete pass);
- each batch is its own short transaction, so a crash mid-loop leaves prior batches durably
  committed;
- best-effort on failure — logs WARN, increments `retention.purge.failure`, short-circuits the loop,
  never rethrows;
- single-node overlap-safe via `ReentrantLock.tryLock()`; a distributed lock is deferred.

Disabled in the `test` profile (`audit.purge.enabled: false`) so surefire runs deterministically;
enabled in `it` and `local`/production. Emits low-cardinality metrics `retention.purge.started`,
`retention.purge.deleted`, `retention.purge.failure`, `retention.purge.duration` (tag: `table`
only). No admin cross-user retention API — retention is time-based, not user-scoped.
