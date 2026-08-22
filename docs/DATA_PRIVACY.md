# Data Privacy
## Agentic AI Task Orchestrator

> Conceptual policy. Guides how user data, prompts, outputs, logs, and external providers are handled. Applied as features are built.

> **Milestone 4 status:** the LLM layer is live and **local-first**. Prompts and model output are
> **never logged in full** (metadata only: provider, model, duration, outcome). The AI layer sends
> **no database/Task/Customer data** to the model in M4 — only the caller's own request text. No
> external provider is wired (`LLM_FALLBACK_ENABLED=false`), so no user data leaves the machine.
> Model output is treated as untrusted (validated before use).
>
> **Milestone 3 status:** the first user content is stored — `tasks` (title/description/…) and
> `customers` (name/email/phone/status). It is kept minimal and **owner-scoped** (a user only ever
> reads their own rows; a non-owner gets 404). Domain logs contain **ids only** (`task.created id=… owner=…`)
> — never titles, names, or emails. `customers.email`/`phone` are PII stored in Postgres and never logged.

> **Milestone 2 status:** credentials handling is now implemented and enforced. Passwords are stored only as **BCrypt hashes** (never plaintext), never returned by any API, never logged. JWTs and `JWT_SECRET` are never logged; tokens carry no password/hash/secret. **Email is PII** stored in Postgres; it appears in logs only as an identifier for failed-login (brute-force) analysis, not alongside credentials. The only committed secret is a clearly-labeled **test-only** JWT value in `application-test.yml`; real secrets come from the environment.

## 1. Data categories

| Category | Examples | Handling |
|---|---|---|
| Credentials | passwords, JWTs, `JWT_SECRET`, API keys | Never logged, audited, cached, or sent to a model. BCrypt for passwords; env for secrets. |
| User content | task titles/descriptions, customer records | Stored in Postgres, owner-scoped. Redacted/summarized in logs and audit. |
| Prompts & model I/O | objectives, tool observations, model output | Not logged in full; delimited as untrusted; not sent externally unless fallback enabled. |
| Operational metadata | ids, timestamps, metrics | Logged/audited with correlation/execution ids; no sensitive content in metric labels. |

## 2. Local-first by default

Development uses **Ollama locally**; user data does not leave the machine. `LLM_FALLBACK_ENABLED=false` by default.

## 3. External model providers (opt-in only)

Sending data to an external provider (e.g. OpenAI) is **off by default**. It may be enabled only when:
1. `LLM_FALLBACK_ENABLED=true` is set deliberately, and
2. a privacy review confirms the data categories involved are acceptable to send, and
3. the behavior is disclosed.

Even then: never send credentials, never send other users' data, and minimize the payload.

## 4. What must NOT be sent to any external service

Passwords, tokens, secrets, keys; another user's data; full audit records; anything beyond the minimum needed for the task.

## 5. Logging & redaction

- Redact or omit sensitive content in logs and audit; store ids/references and short summaries instead of raw payloads (`OBSERVABILITY.md`, `AUDIT_LOGGING.md`).
- Never place sensitive data in URL query parameters.
- Never log full prompts or full tool payloads.

## 6. Retention (targets, to be finalized)

- Durable execution records + audit: retained for the demo's lifetime; a retention/rotation policy is an ADR when the system is deployed.
- Redis ephemeral state: expires by TTL (`MEMORY.md`).
- Operational logs: rotated per deployment config.

## 7. User data rights (future)

Deletion/export of a user's data (tasks, customers, execution history) is a planned capability; when added, cascade rules and audit implications are documented here and in `DATABASE.md`.

## 8. Testing

Assert that no secret/PII appears in logs, audit records, or outbound requests; that external calls are gated by the fallback flag; and that redaction is applied on sensitive fields (`TESTING.md`).

## Milestone 7 — Conversation memory (IMPLEMENTED)

- **What is stored:** per conversation, the user's messages, the assistant's final responses, and
  bounded tool-result summaries — as one JSON blob under `conv:{userId}:{conversationId}`. **Never**
  passwords, tokens, security context, or full entity graphs.
- **Why:** short-term dialogue continuity so a user can follow up on a prior turn.
- **Retention:** sliding 24h TTL (`AGENT_MEMORY_TTL_SECONDS`), refreshed on each turn; idle
  conversations expire automatically. Bounded to 50 msgs / 12,000 chars (older content trimmed).
- **Ownership & deletion:** a conversation is readable only by its owner; `DELETE
  /api/v1/agent/conversations/{id}` lets a user delete their own memory immediately. Redis holds no
  durable record — losing it loses only convenience (durable audit is the separate M9 concern).
- **Logging:** conversation content is never logged; metrics carry only counts/sizes and status, never
  raw text or ids.

## Milestone 8 — Confirmation & guardrail data (IMPLEMENTED)

Confirmation records (`guard:confirmation:{id}`) store the minimum needed to execute the approved
action: owner userId, conversationId, tool name, the validated arguments, riskLevel, and the integrity
fingerprint — as a plain application-owned JSON blob, TTL'd (default 300s), never containing tokens,
secrets, or security context. Client-facing responses expose only a safe confirmation view
(confirmationId, tool, riskLevel, summary, expiresAt) — never internal class names, fingerprints, raw
arguments, or prompt content. Guardrail metrics/logs carry no raw arguments or user text. See
`GUARDRAILS.md`, ADR-0024.

## Milestone 9 — Durable audit data (IMPLEMENTED)

The agent audit tables (`agent_executions`/`agent_steps`/`tool_executions`) store the **minimum** needed
to reconstruct what happened: ids, status/outcome, tool name, risk level, error codes, counts,
durations/timestamps, correlation ids, an `arguments_hash` (SHA-256 of canonical args), and
length-capped, redacted summaries (`final_response_summary`, `result_summary`; caps via `audit.*`).
**Never stored:** raw prompts, raw tool arguments, raw tool results, system prompts, hidden reasoning /
**chain-of-thought**, JWTs, passwords, or secrets. `conversation_id` is stored as a correlation id only
— never the Redis memory blob. Read APIs return sanitized DTOs. See ADR-0028.

## Milestone 10 — Correlation IDs and retention enforcement (IMPLEMENTED)

Only two new MDC keys are populated by M10 and they contain **UUIDs only**:

| MDC key | Value | Set by | Cleared by |
|---|---|---|---|
| `requestId` | UUIDv4 (accepted from `X-Request-Id` iff it parses as UUID; otherwise minted) | `RequestIdFilter` | filter `finally` |
| `executionId` | M9 `agent_executions.execution_uid` (UUID) | `AgentOrchestrator` | orchestrator `finally` |

`X-Request-Id` is not trusted verbatim — a junk header (a path fragment, injection attempt, etc.) is
discarded and a fresh UUIDv4 is minted, so an attacker cannot poison logs or MDC via that header.
No new content is logged. Prompts, tool arguments, JWTs, and confirmation IDs remain out of both
logs and metric tags (ADR-0030 cardinality rule).

Audit retention is now **enforced** (M9 documented the horizon; M10 shipped the purge). The
scheduled `AuditRetentionJob` deletes `agent_executions started_at < now - retentionDays` in
bounded batches, cascading `agent_steps` and `tool_executions` via existing FKs. No new column is
read or written; no privacy surface changes. See ADR-0031.
