# M10 — Observability & Retention Enforcement — DESIGN SPEC

**Status:** DESIGN (2026-08-22, pending approval — no code changes).
**Prerequisites:** M4–M9 implemented + verified.
**Boundary:** M10 turns the metrics/logging/health/audit foundations shipped in M4–M9 into a coherent, low-cardinality operational layer and enforces the previously-documented audit retention horizon. It does **not** deploy Prometheus, Grafana, Elasticsearch, OTel, Kafka, or any external aggregation infrastructure; it merely makes the app compatible with them through standard Micrometer/logging conventions.

---

## 1. Scope

**In scope (M10 IMPLEMENTED):**
1. Consolidated Micrometer metric taxonomy — inventory, normalize, freeze; **no renames**, **no duplicates**, **cardinality rule enforced**.
2. Correlation IDs — request-wide `requestId` MDC + response header; `executionId` MDC on the agent path.
3. Structured logging cleanup — pattern updated to include MDC fields; sensitive-content redaction reaffirmed.
4. Prometheus scrape endpoint via `micrometer-registry-prometheus`; Actuator exposure widened to `health, info, prometheus`.
5. Health/readiness semantics documented; **no custom health indicators** (auto-configured DB + Redis indicators kept as-is).
6. **Audit retention enforcement** — scheduled `AuditRetentionJob` that purges `agent_executions` older than `AGENT_AUDIT_RETENTION_DAYS`, in bounded batches, best-effort, single-node overlap-safe.
7. Retention metrics (`retention.purge.*`).
8. Tests (unit + Testcontainers integration) for the retention job and the MDC filter.
9. Documentation and 2 focused ADRs.

**Out of scope (deferred; documented):**
- Prometheus/Grafana/Loki/OTel deployment (M12 compose).
- Custom application analytics endpoint (no real requirement).
- Distributed tracing, log aggregation, alerting SaaS, ML anomaly detection.
- Admin cross-user retention API (retention is time-based only; no user-facing API).

---

## 2. Locked design decisions

### D1 — Metric taxonomy is frozen; no renames
Every metric emitted by M4–M9 stays under its existing name. M10 does not rebrand `guardrail.deny` → `agent.guardrail.deny` (etc.) — that would silently break any downstream that already scrapes them and would violate `.claude/rules/documentation.md` (behavior changes need a real reason). The full canonical list lives in §3 and in `docs/OBSERVABILITY.md`.

### D2 — Cardinality rule is a hard constraint
No metric tag may carry: `userId`, `conversationId`, `executionId`, `requestId`, `confirmationId`, raw tool arguments, prompt text, or any other unbounded value. Allowed low-cardinality tags: `tool`, `risk`/`riskLevel`, `provider`, `model`, `op`, `outcome`, `status`, `stepType`, `memoryStatus`, `kind`, `limit`, `table`. `tool` is bounded because tools are registered explicitly in the M5 `ToolRegistry` — never user-generated. Documented in `docs/OBSERVABILITY.md`; audited by a `MetricCardinalityTest` that boots the app and asserts no meter name carries a forbidden tag key.

### D3 — Correlation via MDC, not new IDs
The M9 `agent_executions.request_id` and `execution_uid` columns already exist; M10 only propagates them into SLF4J MDC and logs so an operator can pivot request → execution → audit row. A servlet filter (`RequestIdFilter`) at the edge:
- accepts a client-supplied `X-Request-Id` when it looks like a UUID (defense: reject anything else, generate a fresh UUID);
- puts it into MDC key `requestId`;
- echoes it back on `X-Request-Id` response header;
- **clears MDC in `finally`** (thread pool safety).

`AgentOrchestrator` pushes `executionId` into MDC at the start of the loop and clears it at the end (same `try/finally` discipline). No new IDs are minted — the existing execution UUID is reused. The `AgentAuditEmitter` already carries `requestId` into the audit row.

### D4 — Logback pattern update
`application.yml` gets a `logging.pattern.console` that renders MDC fields:
```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{requestId:-}] [%X{executionId:-}] %logger{36} - %msg%n
```
Same pattern in `application-local.yml`; JSON layout is deferred to M12 (compose profile). No new dependency required.

### D5 — Prometheus scrape endpoint
Add `io.micrometer:micrometer-registry-prometheus` (single dependency; Spring Boot auto-configures `/actuator/prometheus`). Widen `management.endpoints.web.exposure.include` to `health, info, prometheus`. Update `SecurityConfig` to whitelist `/actuator/prometheus` alongside the existing `/actuator/health` route — documented that in prod, network-level access control (firewall/reverse-proxy) is the intended production restriction, not a Spring Security ACL. This is called out explicitly so M10 does not appear to solve production auth for /actuator; a follow-up ADR can add a scrape-token later.

### D6 — Health/readiness semantics
No custom `HealthIndicator` beans. Rely on Spring Boot auto-config:
- `DataSourceHealthIndicator` → gates readiness (DB is required).
- `RedisHealthIndicator` → included in `/actuator/health` but **downgraded** to non-critical for readiness via `management.endpoint.health.group.readiness.include=db` (Redis outage degrades agent memory to stateless per M7 hybrid semantics; the service is still "ready").
- **Ollama is not health-checked at all.** LLM failures surface per-request as `LLM_UNAVAILABLE` (M4) — the service must remain ready even when Ollama is down; that is by design.

Enable `management.endpoint.health.probes.enabled: true` so `/actuator/health/liveness` and `/actuator/health/readiness` are separately reachable.

### D7 — Retention enforcement scheduling
`@EnableScheduling` added on a new `@Configuration` `SchedulingConfig` (dedicated, so it is easy to disable per profile). One job: `AuditRetentionJob` with `@Scheduled(cron = "${audit.purge.cron:0 15 3 * * *}", zone = "UTC")` — nightly at 03:15 UTC. All timing configurable via env:
- `AGENT_AUDIT_PURGE_ENABLED=true` (default true; **false** in `application-test.yml`).
- `AGENT_AUDIT_PURGE_CRON=0 15 3 * * *`
- `AGENT_AUDIT_PURGE_BATCH_SIZE=500`
- `AGENT_AUDIT_PURGE_MAX_BATCHES=100` (per-invocation ceiling → ≤ 50 000 rows/run; more waits for the next scheduled tick).

### D8 — Purge SQL & FK strategy
Retention purges `agent_executions` **parent-first** and relies on the existing `ON DELETE CASCADE` FKs on `agent_steps.execution_id`, `tool_executions.execution_id`, and `tool_executions.step_id` (see `V5__create_agent_audit.sql`). No new migration required; no explicit child-delete pass.

Cutoff column: **`started_at`** (NOT NULL on `agent_executions`, indexed via `idx_agent_exec_owner_started`). Rationale:
- `completed_at` can be NULL (stuck / crashed runs) → would strand rows.
- `created_at` and `started_at` differ only by microseconds in practice; `started_at` is the semantic "when this execution happened" field and matches how the read API sorts.

Batched delete (PostgreSQL + H2 PG-mode compatible):
```sql
DELETE FROM agent_executions
 WHERE id IN (
   SELECT id FROM agent_executions
    WHERE started_at < :cutoff
    ORDER BY started_at
    LIMIT :batch
 )
```
- Loop: run up to `MAX_BATCHES` batches per invocation; stop early when a batch returns 0.
- Each batch is its own short `@Transactional` unit (never a single long transaction) → does not block writers for long, and a crash mid-purge leaves prior batches committed.
- Per-batch `rowsDeleted` is added to `retention.purge.deleted{table="agent_executions"}`.

### D9 — Purge failure semantics
Best-effort. If a batch throws:
- WARN log `retention.purge.batch_failed table=… error=…` (class name only, no PII).
- Increment `retention.purge.failure{table="agent_executions"}`.
- **Break the loop for this run** (do not keep hammering a failing DB); return.
- Do **not** rethrow — scheduler must not surface as an application health failure.
The next scheduled tick tries again.

### D10 — Overlap protection
Single-node app → in-process `ReentrantLock.tryLock()` at job entry. If lock is not free, log INFO `retention.purge.skipped_overlap` and return (no counter — this is a normal event). Documented limitation: multi-node deployments would need a DB advisory lock (`pg_try_advisory_lock`) — deferred, no requirement yet.

### D11 — No new operational summary endpoint
The user story is "operator can see what's happening"; Prometheus + `/actuator/health` cover that. A bespoke JSON summary would duplicate metrics with worse cardinality control. Rejected.

### D12 — Log levels
No systemic level changes; existing INFO/WARN/ERROR discipline is correct. One reaffirmation:
- Purge job logs INFO once per run (`retention.purge.completed table=… batches=… deleted=… durationMs=…`) — one line per night, not per batch.
- Skipped-overlap = INFO, batch failure = WARN, unexpected = ERROR (never happens if D9 holds).

### D13 — What M10 explicitly does not change
- Existing metric names — frozen (D1).
- Existing log statements — kept; only the pattern changes (D4).
- Existing endpoints — untouched.
- Existing schema — untouched (D8 uses `V5`).
- Existing agent/tool/guardrail/audit/memory code paths — no functional change; observability is added around them.

---

## 3. Canonical metric taxonomy (M10-frozen)

Grouped by source layer. Every name is **already emitted** unless marked `NEW`. Every listed tag is bounded per D2.

### HTTP (auto)
| Metric | Type | Tags | Source |
|---|---|---|---|
| `http.server.requests` | timer | `method`, `uri`, `status`, `outcome` | Spring Boot auto |

`uri` is bounded to the registered route templates (Spring auto-normalizes `/api/v1/agent/executions/{id}`), not raw request paths — so cardinality stays bounded even with UUID path params.

### LLM (M4)
| Metric | Type | Tags |
|---|---|---|
| `llm.request.duration` | timer | `op`, `provider`, `model`, `outcome` |
| `llm.request.result` | counter | `op`, `provider`, `model`, `outcome` |

### Tools (M5)
| Metric | Type | Tags |
|---|---|---|
| `tool.execution.duration` | timer | `tool`, `risk`, `outcome` |
| `tool.execution.result` | counter | `tool`, `risk`, `outcome` |

### Agent (M6)
| Metric | Type | Tags |
|---|---|---|
| `agent.execution.count` | counter | `status` |
| `agent.iterations` | distribution summary | *(untagged)* |
| `agent.tool.calls` | counter | *(untagged)* — bounded per-call increments |
| `agent.loop.detected` | counter | *(untagged)* |
| `agent.limit.reached` | counter | `limit` ∈ {`iteration`, `tool_call`} |
| `agent.execution.duration` | timer | `status` — **NEW** (currently only `.count` is emitted; the orchestrator already records duration into logs and into audit's `duration_ms` — surface it as a timer too, low-cardinality tag) |

### Conversation memory (M7)
| Metric | Type | Tags |
|---|---|---|
| `memory.load` | timer | *(untagged)* |
| `memory.append` | timer | *(untagged)* |
| `memory.trim` | counter | *(untagged)* |
| `memory.hit` | counter | *(untagged)* |
| `memory.miss` | counter | *(untagged)* |
| `memory.unavailable` | counter | `op` ∈ {`load`,`append`,`delete`,`degrade`} |
| `agent.conversation` | counter | `memoryStatus` |

### Guardrails (M8)
| Metric | Type | Tags |
|---|---|---|
| `guardrail.allow` | counter | `tool`, `riskLevel` |
| `guardrail.deny` | counter | `tool`, `riskLevel` |
| `guardrail.policy_violation` | counter | `tool`, `riskLevel` |
| `guardrail.confirmation_required` | counter | `tool`, `riskLevel` |
| `guardrail.confirmation_approved` | counter | `riskLevel` |
| `guardrail.confirmation_expired` | counter | *(untagged)* |
| `guardrail.rate_limited` | counter | *(untagged)* |

### Audit (M9)
| Metric | Type | Tags |
|---|---|---|
| `audit.execution.created` | counter | *(untagged)* |
| `audit.step.created` | counter | `stepType` |
| `audit.tool_execution.created` | counter | `outcome` |
| `audit.write.success` | counter | *(untagged)* |
| `audit.write.failure` | counter | `kind` |

### Retention (M10 — **NEW**)
| Metric | Type | Tags |
|---|---|---|
| `retention.purge.started` | counter | `table` |
| `retention.purge.deleted` | counter | `table` — incremented by batch row count |
| `retention.purge.failure` | counter | `table` |
| `retention.purge.duration` | timer | `table` |

**Explicitly forbidden tags anywhere:** `userId`, `conversationId`, `executionId`, `requestId`, `confirmationId`, raw arguments, raw prompt text, raw result summaries.

---

## 4. Correlation / MDC contract

| Key | Set by | Cleared by | Present on |
|---|---|---|---|
| `requestId` | `RequestIdFilter` (edge) | filter `finally` | every log line during a request |
| `executionId` | `AgentOrchestrator` (loop start) | orchestrator `finally` | every log line during an agent run (nested inside `requestId`) |

Header contract:
- Request accepts `X-Request-Id`. If missing or not a UUID → filter mints a fresh UUIDv4.
- Response always carries `X-Request-Id` (echoed or minted). Documented in `docs/API.md`.

Never in MDC (or logs): JWT, password, prompt text, tool arguments, confirmation IDs, chain-of-thought.

---

## 5. Health / readiness

Reachable endpoints after M10:

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Overall status (DB + Redis health from auto-config, no LLM). |
| `GET /actuator/health/liveness` | Process alive (Boot default probe). |
| `GET /actuator/health/readiness` | DB reachable (Redis excluded from group — memory degrades gracefully; Ollama excluded — LLM is per-request). |
| `GET /actuator/info` | App name + version (unchanged). |
| `GET /actuator/prometheus` | Prometheus scrape output. |

Security whitelist (existing `SecurityConfig`): `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`.

---

## 6. Retention design

### 6.1 Contract
- **What:** rows in `agent_executions` (and their cascaded children) with `started_at < now(UTC) - retentionDays`.
- **When:** at `AGENT_AUDIT_PURGE_CRON` (default `0 15 3 * * *`, UTC).
- **How:** batched DELETE via subquery-with-LIMIT; each batch in its own `REQUIRES_NEW`; cap per-invocation batches; short-circuit on batch failure; overlap-safe via `ReentrantLock.tryLock()`.
- **Guarantees:** never deletes rows newer than the cutoff; never blocks agent writes for long; never crashes the app on failure.

### 6.2 Class layout (new)
```
com.prince.agentic.audit.retention
├── AuditRetentionProperties.java     // @ConfigurationProperties("audit.purge")
├── AuditRetentionJob.java            // @Scheduled entrypoint; ReentrantLock; loop
├── AuditRetentionRepository.java     // custom @Repository with the batched DELETE
└── SchedulingConfig.java             // @Configuration @EnableScheduling
```

### 6.3 Configuration keys (all env-overrideable)
```yaml
audit:
  purge:
    enabled: ${AGENT_AUDIT_PURGE_ENABLED:true}
    cron: ${AGENT_AUDIT_PURGE_CRON:0 15 3 * * *}
    batch-size: ${AGENT_AUDIT_PURGE_BATCH_SIZE:500}
    max-batches: ${AGENT_AUDIT_PURGE_MAX_BATCHES:100}
```
`retentionDays` reuses the existing `AuditProperties.retentionDays` — one source of truth.

### 6.4 Test profile overrides
`application-test.yml` sets `audit.purge.enabled: false` so `@SpringBootTest` never triggers the job by accident. Integration tests invoke `AuditRetentionJob.runOnce()` directly.

---

## 7. Security & privacy review

| Surface | Risk | Mitigation |
|---|---|---|
| `/actuator/prometheus` | leaks aggregate rates | tag cardinality rule (D2); no per-user labels; production access = network ACL (documented) |
| Log lines | MDC IDs could leak into a shared logger | filter/orchestrator `finally` clears MDC; unit-tested |
| Retention job | could delete recent rows if cutoff is wrong | cutoff computed from injected `Clock` (deterministic in tests); SQL predicate is `< :cutoff` (never `<=`); integration test proves recent rows survive |
| Retention metrics | none — labels are `table` only | ok |
| Info endpoint | already narrow (app name + version) | unchanged |

No new sensitive fields are logged, stored, or exposed. `X-Request-Id` echo is a UUID — no user data.

---

## 8. Testing

### 8.1 Unit
- `RequestIdFilterTest` — header accepted (valid UUID) / rejected (junk → fresh UUID) / MDC set + cleared / response header present.
- `AgentOrchestratorMdcTest` — MDC `executionId` set on entry, cleared on exit even on exception.
- `AuditRetentionPropertiesTest` — defaults, validation.
- `AuditRetentionJobTest` — happy path (invokes repo up to N batches), zero-row early exit, batch-failure short-circuit + metric, overlap guard (second call skips), disabled flag noop.
- `MetricCardinalityTest` — boot the app in a minimal slice, exercise each metric-emitting service, walk `MeterRegistry.getMeters()`, assert no meter has a tag key in the forbidden set.

### 8.2 Integration (Testcontainers Postgres + Redis)
- `AuditRetentionIT` — seed 5 executions across cutoff (with children); run job; assert old rows + cascaded steps + tool_executions gone; assert fresh rows + children untouched; assert `retention.purge.deleted` incremented by the exact count.
- `AuditRetentionBatchingIT` — seed > `batchSize` old rows; assert multiple batches; assert `maxBatches` cap honored.
- `PrometheusEndpointIT` — GET `/actuator/prometheus` returns 200 as anonymous; body contains one of the frozen metric names (e.g. `guardrail_allow_total`).
- `RequestIdEndToEndIT` — hit an existing agent endpoint, assert `X-Request-Id` present in response; hit again with a supplied UUID, assert echoed; hit with junk, assert a fresh UUID is minted.
- `ReadinessIT` — with Redis stopped (Testcontainers stop), assert `/actuator/health/readiness` still 200 and `/actuator/health` shows Redis DOWN.

### 8.3 Coverage
Target unchanged: overall ≥ 75%, service/domain ≥ 80%. Retention code is small and mostly boundary logic; tests above cover it.

---

## 9. CI compatibility

- Adds one Maven dependency (`micrometer-registry-prometheus`). No plugin changes.
- Adds no shell scripts. `mvnw` executable bit already fixed (commit `97eb7a8`).
- `application-test.yml` disables the purge scheduler → CI `./mvnw -B --no-transfer-progress clean verify` remains deterministic.
- No new Docker images. No new services in Testcontainers beyond existing PG/Redis.

---

## 10. Documentation touch-list

- `docs/OBSERVABILITY.md` — canonical M10 metric table (§3), MDC contract (§4), health map (§5).
- `docs/AUDIT_LOGGING.md` — M10 retention enforcement section (replaces "no purge scheduler in M9").
- `docs/DATABASE.md` — note that retention purges `agent_executions` parent-first via CASCADE (no schema change).
- `docs/SECURITY.md` — `/actuator/prometheus` whitelist rationale + prod ACL note.
- `docs/DATA_PRIVACY.md` — reaffirm nothing new is logged; MDC contents enumerated.
- `docs/PERFORMANCE.md` — batched delete rationale, per-invocation ceiling.
- `docs/DEPLOYMENT.md` + `.env.example` — new env keys.
- `docs/TESTING.md` — retention IT + MDC tests listed.
- `docs/ROADMAP.md` — mark M10 IMPLEMENTED after execution.
- `docs/CHANGELOG.md` — entry.
- `README.md` + `backend/README.md` — 1-line M10 status update.
- `docs/ADR/README.md` — index ADR-0030 and ADR-0031.

---

## 11. ADRs to create (2)

- **ADR-0030 — Observability metric taxonomy freeze + correlation IDs.** Records D1 (no renames), D2 (cardinality rule), D3–D4 (MDC + logback pattern), D5–D6 (Prometheus + health group semantics). One ADR because these choices are one cohesive stance ("keep what M4–M9 built; add the plumbing to observe it").
- **ADR-0031 — Audit retention enforcement (scheduled, batched, best-effort).** Records D7–D10 (schedule, cutoff column, SQL, failure semantics, overlap). Numbering follows the existing 0026–0029 audit series.

No third ADR for health semantics — folded into ADR-0030 (they are cheap to co-locate and health is a small decision here).

---

## 12. Self-review against prior milestones

| Concern | Verdict |
|---|---|
| Duplicate metric names with M4–M9 | None — M10 adds only `agent.execution.duration` and `retention.purge.*`. Everything else is frozen. |
| Cardinality violations in existing metrics | Audited via `MetricCardinalityTest` (§8.1) — expected to pass because M4–M9 already followed the rule; test locks it in. |
| Observability breaking business execution | Retention job never rethrows (D9); MDC filter is a servlet filter with `try/finally` clearing; Prometheus endpoint is Boot auto-config (no code path change). |
| Purge deleting fresh data | SQL predicate `started_at < :cutoff` (strict); IT (`AuditRetentionIT`) proves fresh rows survive. |
| Purge blocking writers | Batched DELETE; each batch its own `REQUIRES_NEW` short transaction; no `FOR UPDATE` locks; `MAX_BATCHES` ceiling. |
| Scheduler overlap | `ReentrantLock.tryLock()`; skipped runs log INFO. |
| Sensitive logging | MDC keys are UUIDs only; logback pattern change adds two IDs, no content; all prior redaction rules stand. |
| CI regression | Purge disabled in test profile; only 1 new dependency; wrapper permissions untouched. |
| Readiness wrongly failing when Redis/Ollama down | Explicitly excluded from the readiness group (D6); ReadinessIT proves it. |

Contradictions found: **none**. The pre-existing OBSERVABILITY.md §3 metric table listed some slightly different names (e.g. `agent.tool.calls (by tool, outcome)`) that were never implemented that way in M6 (M6 emits `agent.tool.calls` untagged and `tool.execution.result` tagged). The M10 execution updates OBSERVABILITY.md §3 to match what the code actually emits — that is a docs correction, not a code change.

---

## 13. Open questions / deliberately deferred

- **Prometheus endpoint authentication.** Deferred — network ACL at deploy time. If/when the app is exposed publicly we would add a scrape token (new ADR).
- **JSON logs.** Deferred to M12 (docker compose profile), where a `logstash-logback-encoder` layout can be added without changing local dev.
- **Multi-node purge coordination.** Deferred — no requirement.
- **Distributed tracing.** Deferred — Boot 3 supports Micrometer Tracing (Brave/OTel); no scope in M10.

---

## 14. Definition of Done for M10

- Metric taxonomy §3 exactly matches what the running app exposes on `/actuator/prometheus`.
- `X-Request-Id` round-trips on every response; MDC-cleared in all thread pools.
- `/actuator/health/readiness` is 200 with Redis down; 503 with Postgres down.
- Retention job deletes only rows older than `retentionDays`, in bounded batches, and never rethrows.
- `./mvnw verify` green (coverage gate held); new ITs pass on real Postgres.
- Every listed doc updated; two ADRs written; ROADMAP shows M10 IMPLEMENTED.
