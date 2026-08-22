# ADR-0030 — Observability Metric Taxonomy Freeze + Request Correlation

**Status:** Accepted · **Milestone:** M10 · **Date:** 2026-08-22

## Context
Milestones M4–M9 each introduced their own Micrometer metrics and structured log fields as the layer
was added. M10 needs to make the resulting surface coherent — one taxonomy, low-cardinality by
construction, correlatable end-to-end — without deploying Prometheus/Grafana/OTel infrastructure
(those live in M12+). The natural temptation is to *rename* existing metrics under a unified prefix
(`agent.audit.write.success`, `agent.guardrail.allow`, etc.). That would silently break every
existing dashboard or alert already scraping the current names and violates the "don't rename what
works" principle from `.claude/rules/documentation.md`.

## Decision

### D1 — Metric names are frozen
No M4–M9 metric is renamed. The canonical list lives in `docs/OBSERVABILITY.md` §3. M10 adds only:

- `agent.execution.duration` (Timer, tag `status`) — filled a small gap in M6 which recorded only the
  matching `agent.execution.count`. Already present in the codebase; documented in M10.
- `retention.purge.{started,deleted,failure,duration}` — new, and covered separately by ADR-0031.

### D2 — Cardinality rule is enforced (not merely documented)
No metric tag key may be any of: `userId`, `conversationId`, `executionId`, `requestId`,
`confirmationId`, `arguments`, `argumentsHash`, `prompt`, `promptText` (or `_` variants). The bounded
allowed tag keys are: `tool`, `risk`/`riskLevel`, `provider`, `model`, `op`, `outcome`, `status`,
`stepType`, `memoryStatus`, `kind`, `limit`, `table`, `errorCode`, `method`, `uri`. `tool` is bounded
because tools are explicitly registered in the M5 `ToolRegistry` — never user-generated.
`MetricCardinalityTest` boots the app and asserts no meter carries a forbidden tag key.

### D3 — Correlation via MDC, reusing existing IDs
An edge servlet filter (`RequestIdFilter`, `Ordered.HIGHEST_PRECEDENCE`) resolves the request id:
- accepts `X-Request-Id` only when it parses as a UUID (defence against MDC/log-injection);
- otherwise mints a fresh UUIDv4;
- places it into MDC key `requestId`;
- echoes it back on the response header;
- clears MDC in `finally` (thread-pool safety).

`AgentOrchestrator` pushes its existing `executionUid` into MDC key `executionId` for the loop only,
restoring the prior value in `finally`. No new IDs are minted — the M9 `agent_executions.execution_uid`
and the audit-carried `request_id` are the same values that appear in logs.

### D4 — Logback pattern is updated once
`application.yml` sets `logging.pattern.console` to include `[%X{requestId:-}] [%X{executionId:-}]`
so console output carries both IDs whenever they are set. Bracketed empty on non-agent requests.
JSON logs remain deferred to M12 (compose profile).

### D5 — Prometheus scrape endpoint is exposed
Add `io.micrometer:micrometer-registry-prometheus` (version from the Boot BOM), widen
`management.endpoints.web.exposure.include` to `health,info,prometheus`, and whitelist
`/actuator/prometheus` in `SecurityConfig`. Production access is restricted at the **network layer**
(reverse proxy / firewall) — Spring Security is not the enforcement point here (a scrape token can
be added later with its own ADR). `management.endpoint.prometheus.access: read_only` and
`management.prometheus.metrics.export.enabled: true` are set explicitly for clarity.

### D6 — Health/readiness semantics
No custom `HealthIndicator` beans. Rely on Boot auto-config plus:
- `management.endpoint.health.probes.enabled: true` exposes `/actuator/health/{liveness,readiness}`.
- `management.endpoint.health.group.readiness.include: db` — Redis and Ollama are deliberately
  **excluded** from readiness (Redis outage degrades M7 memory to stateless; Ollama failures surface
  per-request as `LLM_UNAVAILABLE`, per ADR-0019 / M4). The service must remain ready when either
  dependency is degraded.

## Consequences
- No dashboards/alerts break; the taxonomy is documented and enforced.
- Every log line during an HTTP request carries `requestId`; every log line during an agent run
  additionally carries `executionId`, and both correlate to the M9 audit row.
- `/actuator/prometheus` is scrape-ready without new infrastructure.
- Readiness now truthfully reflects the guarantee we make (DB required; degraded Redis is still
  usable).

## Alternatives considered
- **Rename everything under an `agentic.*` prefix.** Rejected — silently breaks any scraper and adds
  zero information over the current, already-consistent per-layer prefixes.
- **Add a bespoke JSON summary endpoint.** Rejected — duplicates Prometheus with weaker cardinality
  control and no real user requirement (`docs/superpowers/specs/2026-08-22-m10-observability-design.md`
  §2 D11).
- **Include Redis/Ollama in readiness.** Rejected — would cause spurious "not ready" reports the
  moment those degrade, contradicting M7's hybrid-degradation contract (ADR-0019) and M4's
  request-scoped LLM error semantics.

## Verification
`RequestIdFilterTest`, `ObservabilityEndpointsTest`, `MetricCardinalityTest`, plus every existing
M4–M9 test remains green. `./mvnw verify` reports 383 unit + 44 integration tests passing;
`/actuator/prometheus` returns 200 and includes `jvm_*` metrics; readiness/liveness probes are
separately reachable.
