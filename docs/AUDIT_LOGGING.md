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
