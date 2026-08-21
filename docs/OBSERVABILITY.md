# Observability
## Agentic AI Task Orchestrator

> Full observability is planned (M10). Covers both normal backend requests and AI/tool execution.

> **Milestone 1 status:** structured SLF4J/Logback logging is active with per-profile levels; Actuator exposes `health`+`info` only. Error responses carry a **per-response `traceId`** (a generated UUID, logged alongside the response) — a real, honest identifier, but **not** yet request-wide correlation. MDC-based correlation-ID propagation and the Micrometer/Prometheus/Grafana metrics below are PLANNED (M10).

> **Milestone 2 status:** security-relevant events are logged (INFO: successful registration/login by user id; WARN: failed login attempts, unauthorized/forbidden requests, rejected bearer tokens by exception type). **Never logged:** passwords, password hashes, raw JWTs, or the JWT secret (`DATA_PRIVACY.md`). Login is logged by user id (and, on failure, the attempted email for brute-force analysis) — not the token or password.

> **Milestone 3 status:** domain lifecycle events are logged at INFO with **ids only** —
> `task.created|updated|deleted id={} owner={}` and `customer.created|updated|deleted id={} owner={}`.
> **Never logged:** task titles/descriptions, or customer names/emails/phones (`DATA_PRIVACY.md`).
> Validation/authorization failures continue to surface through the global handler at WARN with the
> per-response `traceId`.

## 1. Correlation & execution IDs

- **`correlationId`** — one per HTTP request, generated at the edge (or accepted from a trusted header), attached to the logging MDC and propagated through the call.
- **`executionId`** — one per agent run.
- **`toolExecutionId`** — one per tool call within a run.

Every log line, metric exemplar, and audit event carries the ids relevant to its context, so a single agent run is traceable end-to-end: request → decision → tool call → side effect.

## 2. Structured logging

- SLF4J with a structured (JSON in docker/prod) layout; ids in the MDC.
- Levels: DEBUG (dev detail) · INFO (lifecycle: request start/end, run start/complete, tool start/complete) · WARN (recoverable: retry, guardrail near-limit) · ERROR (failures).
- Redaction on: no secrets, tokens, full prompts, or full payloads (`DATA_PRIVACY.md`).

## 3. Metrics (Micrometer → Prometheus)

| Metric | Type | Why |
|---|---|---|
| `http.server.requests` (latency, count, status) | timer | API health, p50/p95/p99 |
| `agent.execution.duration` | timer | How long runs take |
| `agent.execution.result` (completed/failed/incomplete/cancelled) | counter | Success rate |
| `agent.tool.calls` (by tool, outcome) | counter | Tool usage & failures |
| `agent.tool.duration` (by tool) | timer | Tool latency |
| `llm.request.duration` | timer | Model latency |
| `llm.tokens` (prompt/completion, when available) | counter/gauge | Cost/usage |
| `agent.guardrail.trips` (by type) | counter | Safety/abuse signal |
| `agent.authorization.denied` | counter | Security signal |
| `llm.provider.errors` | counter | Provider reliability |

- Keep label cardinality bounded — tool *name* and *outcome* are fine; raw user text or full ids are not.

## 4. Health

- `/actuator/health` (liveness/readiness) with checks for Postgres, Redis, and the LLM provider where meaningful.
- Prometheus scrapes the metrics endpoint; Grafana renders dashboards (defined in compose at M10/M12).

## 5. Dashboards (planned)

- **API overview:** request rate, error rate, latency percentiles.
- **Agent overview:** run success rate, run duration, tool-call distribution, guardrail trips, authorization denials.
- **LLM overview:** request duration, token usage, provider errors.

## 6. Relationship to audit

Observability is for operating the system (may be sampled/rotated); **audit** is the durable record of who-did-what (never dropped) — see `AUDIT_LOGGING.md`. They share ids so an operator can pivot between them.

## 7. Testing

Assert that ids are present and propagated, that key metrics increment on the expected paths, and that no sensitive data appears in logs/metric labels (`TESTING.md`).
