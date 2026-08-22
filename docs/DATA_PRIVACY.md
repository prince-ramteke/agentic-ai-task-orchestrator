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
