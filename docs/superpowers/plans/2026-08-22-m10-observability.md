# M10 — Observability & Retention Enforcement — IMPLEMENTATION PLAN

Sequential, TDD-first. Each task is small, independently verifiable, and ends with a green build. Aligns 1:1 with `docs/superpowers/specs/2026-08-22-m10-observability-design.md`.

**Branch:** `feat/m10-observability` (branched from `main` at `97eb7a8`).
**Verify command (every task):** `./mvnw -B --no-transfer-progress clean verify`.

---

## Task 1 — Add Prometheus registry dependency + expose scrape endpoint
**Files:** `backend/pom.xml`, `backend/src/main/resources/application.yml`, `com.prince.agentic.security.SecurityConfig`.

**Test-first:**
- `PrometheusEndpointIT` (Testcontainers) — GET `/actuator/prometheus` as anonymous returns 200 and body includes `guardrail_allow_total` (fire one guardrail call in an `@BeforeEach` if needed to force emission).

**Change:**
- Add `<dependency>io.micrometer:micrometer-registry-prometheus</dependency>` to `backend/pom.xml` (no version — managed by Boot BOM).
- `application.yml`: `management.endpoints.web.exposure.include: health, info, prometheus`.
- `SecurityConfig`: whitelist `/actuator/prometheus` alongside existing `/actuator/health*`.

**Verify:** IT green; existing tests unaffected.

---

## Task 2 — Health group: readiness excludes Redis, includes only DB
**Files:** `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`.

**Test-first:**
- `ReadinessGroupIT` — `/actuator/health/readiness` returns 200 even when Redis is stopped (`GenericContainer.stop()`), and Postgres reachable.
- Assert `/actuator/health` still shows Redis DOWN under that condition.

**Change:**
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: db
```

**Verify:** IT green; unit test `HealthEndpointTest` (if any) still green.

---

## Task 3 — RequestIdFilter (MDC + response header)
**Files (new):**
- `com.prince.agentic.common.observability.RequestIdFilter` (extends `OncePerRequestFilter`).
- `com.prince.agentic.common.observability.ObservabilityConfig` (`@Configuration`, registers filter as first in chain).

**Test-first:**
- `RequestIdFilterTest` (MockMvc / standalone) — three cases:
  1. no header → response has `X-Request-Id` = a UUID; MDC set + cleared.
  2. valid UUID header → echoed exactly.
  3. junk header (`"not-a-uuid"`) → new UUID minted; MDC cleared even on exception thrown from downstream servlet.

**Change:**
- Filter: read header, validate as UUID via `UUID.fromString` in try/catch; `MDC.put("requestId", …)`; add response header; `try { chain.doFilter } finally { MDC.remove("requestId") }`.
- Register early in `SecurityFilterChain` order (before Spring Security filters via `FilterRegistrationBean` order `Ordered.HIGHEST_PRECEDENCE`).

**Verify:** `./mvnw verify` green.

---

## Task 4 — Logback pattern includes MDC fields
**Files:** `backend/src/main/resources/application.yml`, `application-local.yml`.

**Test-first:**
- `LoggingPatternTest` — capture a log line via a `ListAppender`, assert the rendered message contains bracketed `requestId` when MDC is set.

**Change:**
```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{requestId:-}] [%X{executionId:-}] %logger{36} - %msg%n"
```

**Verify:** unit test green; run app locally, hit `/actuator/health`, confirm request ID appears in console output.

---

## Task 5 — Push `executionId` into MDC inside AgentOrchestrator
**Files:** `com.prince.agentic.agent.AgentOrchestrator`.

**Test-first:**
- `AgentOrchestratorMdcTest` — fake `LlmClient` that captures `MDC.get("executionId")` inside a decision call; assert non-null UUID during the loop; assert MDC is `null` after the loop returns (both success and exception paths).

**Change:** wrap the `execute()` body in `try { MDC.put("executionId", executionUid); … } finally { MDC.remove("executionId"); }`. No behavior change.

**Verify:** `./mvnw verify` green.

---

## Task 6 — Emit `agent.execution.duration` timer
**Files:** `com.prince.agentic.agent.AgentOrchestrator`.

**Test-first:** extend `AgentOrchestratorMetricsTest` — after a run, assert `meters.timer("agent.execution.duration", "status", <status>).count() == 1` and `totalTime(NS) > 0`.

**Change:** wrap the outer loop with a `Timer.Sample`, `sample.stop(Timer.builder("agent.execution.duration").tag("status", status.name()).register(meters))` at each terminal branch. No renames.

**Verify:** green.

---

## Task 7 — MetricCardinalityTest (guardrail on forbidden tag keys)
**File (new):** `com.prince.agentic.common.observability.MetricCardinalityTest` (`@SpringBootTest`, `webEnvironment=NONE`).

**Change:** boot the app; exercise one call in each meter-emitting service via injected beans (fake LLM, fake user, in-memory tool); iterate `meterRegistry.getMeters()`; for each `Meter.getId().getTags()`, assert none of `{userId, conversationId, executionId, requestId, confirmationId, arguments, prompt}` appears as a tag key.

**Verify:** green — should pass with existing code as-is (§12 self-review).

---

## Task 8 — AuditRetentionProperties + config
**Files (new):** `com.prince.agentic.audit.retention.AuditRetentionProperties`.
**Files (change):** `application.yml`, `application-test.yml`, `.env.example`, `MainApplication`/config class to register properties.

**Test-first:** `AuditRetentionPropertiesTest` — defaults resolve (`enabled=true`, `cron="0 15 3 * * *"`, `batchSize=500`, `maxBatches=100`); zeros in the record fall back to defaults (mirror `AuditProperties` pattern).

**Change:**
```java
@Validated
@ConfigurationProperties("audit.purge")
public record AuditRetentionProperties(
    boolean enabled, String cron,
    @Min(1) int batchSize, @Min(1) int maxBatches) {
  public AuditRetentionProperties {
    if (cron == null || cron.isBlank()) cron = "0 15 3 * * *";
    if (batchSize == 0) batchSize = 500;
    if (maxBatches == 0) maxBatches = 100;
  }
}
```
Register via `@EnableConfigurationProperties(AuditRetentionProperties.class)` on an existing `@Configuration`.

Add `application.yml` block from spec §6.3. Test profile: `audit.purge.enabled: false`. `.env.example` gets the four `AGENT_AUDIT_PURGE_*` keys.

**Verify:** green.

---

## Task 9 — AuditRetentionRepository (batched delete)
**Files (new):** `com.prince.agentic.audit.retention.AuditRetentionRepository`.

**Test-first:** `AuditRetentionRepositoryIT` (Testcontainers Postgres + Flyway `V5`):
- Insert 3 old (`started_at = now - 100d`) + 2 fresh executions, each with a step + tool_execution.
- `deleteExpiredBatch(cutoff, 10)` returns 3.
- Assert only fresh executions remain; assert cascaded step/tool rows for old executions are gone.
- Second call returns 0.
- `batchSize=1` with 3 old rows: 3 sequential calls each return 1.

**Change:** JDBC template–based repository (skip JPA to keep the subquery clean):
```java
@Repository
public class AuditRetentionRepository {
  private final JdbcTemplate jdbc;
  public AuditRetentionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public int deleteExpiredBatch(Instant cutoff, int batchSize) {
    return jdbc.update("""
        DELETE FROM agent_executions
         WHERE id IN (
           SELECT id FROM agent_executions
            WHERE started_at < ?
            ORDER BY started_at
            LIMIT ?
         )
        """, Timestamp.from(cutoff), batchSize);
  }
}
```
`REQUIRES_NEW` not needed here — each call is its own transaction (default `PROPAGATION_REQUIRED` inside a job with no outer tx). The job invokes this in a loop without an outer `@Transactional`.

**Verify:** IT green.

---

## Task 10 — AuditRetentionJob
**Files (new):**
- `com.prince.agentic.audit.retention.AuditRetentionJob`.
- `com.prince.agentic.audit.retention.SchedulingConfig` (`@Configuration @EnableScheduling`).

**Test-first:** `AuditRetentionJobTest` (unit, with mocks for repo + `Clock` + `MeterRegistry`):
- happy path: repo returns `batchSize` twice then `0`; job loops 3 times; `retention.purge.deleted` incremented by `2*batchSize`; `retention.purge.duration` observed once; INFO summary logged.
- max-batches cap: repo always returns `batchSize`; job stops after `maxBatches` batches.
- disabled: `enabled=false` → job returns immediately, no repo call, no metrics.
- overlap: two threads call `runOnce()` concurrently → the second returns immediately (INFO), no repo call from the second.
- failure: repo throws on batch 2 → `retention.purge.failure{table=…}` incremented, WARN logged, loop breaks, no rethrow.

**Change:**
```java
@Component
public class AuditRetentionJob {
  private final AuditProperties audit;
  private final AuditRetentionProperties purge;
  private final AuditRetentionRepository repo;
  private final Clock clock;
  private final MeterRegistry meters;
  private final ReentrantLock lock = new ReentrantLock();
  private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);
  private static final String TABLE = "agent_executions";

  @Scheduled(cron = "${audit.purge.cron}", zone = "UTC")
  public void scheduled() { runOnce(); }

  public void runOnce() {
    if (!purge.enabled()) return;
    if (!lock.tryLock()) {
      log.info("retention.purge.skipped_overlap table={}", TABLE);
      return;
    }
    Timer.Sample sample = Timer.start(meters);
    long totalDeleted = 0; int batches = 0;
    meters.counter("retention.purge.started", "table", TABLE).increment();
    try {
      Instant cutoff = clock.instant().minus(Duration.ofDays(audit.retentionDays()));
      for (int i = 0; i < purge.maxBatches(); i++) {
        int n;
        try {
          n = repo.deleteExpiredBatch(cutoff, purge.batchSize());
        } catch (RuntimeException e) {
          meters.counter("retention.purge.failure", "table", TABLE).increment();
          log.warn("retention.purge.batch_failed table={} error={}", TABLE, e.getClass().getSimpleName());
          break;
        }
        if (n == 0) break;
        totalDeleted += n; batches++;
        meters.counter("retention.purge.deleted", "table", TABLE).increment(n);
      }
    } finally {
      sample.stop(Timer.builder("retention.purge.duration").tag("table", TABLE).register(meters));
      lock.unlock();
      log.info("retention.purge.completed table={} batches={} deleted={}", TABLE, batches, totalDeleted);
    }
  }
}
```

`SchedulingConfig`: trivial `@Configuration @EnableScheduling`. Kept dedicated so future scheduled jobs are additive.

**Verify:** unit tests green.

---

## Task 11 — AuditRetentionIT (end-to-end)
**Files (new):** `backend/src/test/java/…/audit/retention/AuditRetentionIT.java`.

Testcontainers Postgres (real Flyway `V5`). Seed 3 old + 2 fresh `agent_executions` with children. Call `AuditRetentionJob.runOnce()` directly (test profile has `enabled=false`, but we call the method by hand — decoupled from `@Scheduled`).

Assert:
- Only the 2 fresh executions remain.
- All child `agent_steps` / `tool_executions` for old rows are cascaded away.
- `retention.purge.deleted{table="agent_executions"}` == 3.
- `retention.purge.duration{table="agent_executions"}.count()` == 1.

Second assertion pass — invoke `runOnce()` again: no more deletions, `deleted` counter unchanged.

**Verify:** IT green.

---

## Task 12 — AuditRetentionBatchingIT (bounds)
**File (new):** `AuditRetentionBatchingIT`.

Seed `batchSize * 3` old rows (using `batchSize=5`, `maxBatches=2` via `@TestPropertySource`). Assert: after one `runOnce()`, exactly `batchSize * maxBatches` = 10 rows deleted; `batchSize` rows remain until the next call.

**Verify:** IT green.

---

## Task 13 — RequestIdEndToEndIT
Hit an existing authenticated endpoint (`GET /api/v1/me` or `GET /api/v1/agent/executions`) via MockMvc / Testcontainers:
1. Assert response `X-Request-Id` present and is a UUID.
2. Send `X-Request-Id: <valid uuid>` → assert echoed exactly.
3. Send `X-Request-Id: ../etc/passwd` → assert response has a fresh UUID (junk not echoed).

**Verify:** IT green.

---

## Task 14 — Documentation updates
Update in one commit (docs-only):
- `docs/OBSERVABILITY.md` — replace §3 with the canonical M10 metric table; add MDC contract + health map from spec §4–§5; add "Milestone 10 — Retention metrics (IMPLEMENTED)" section.
- `docs/AUDIT_LOGGING.md` — add M10 section; supersede the "no purge scheduler in M9" wording.
- `docs/DATABASE.md` — retention paragraph under §0c noting parent-first CASCADE, no schema change.
- `docs/SECURITY.md` — `/actuator/prometheus` public-route rationale + prod ACL guidance.
- `docs/DATA_PRIVACY.md` — MDC contents enumerated; nothing sensitive added.
- `docs/PERFORMANCE.md` — retention batching rationale + per-invocation ceiling.
- `docs/DEPLOYMENT.md` + `.env.example` — new `AGENT_AUDIT_PURGE_*` keys.
- `docs/TESTING.md` — mention new ITs.
- `docs/ROADMAP.md` — mark M10 IMPLEMENTED with dated verification note.
- `docs/CHANGELOG.md` — one entry.
- `README.md` + `backend/README.md` — one-line status bump.
- `docs/ADR/README.md` — index ADR-0030 + ADR-0031.

**Verify:** `./mvnw verify` still green (docs-only).

---

## Task 15 — ADRs
- `docs/ADR/0030-observability-taxonomy-and-correlation.md` — records D1–D6.
- `docs/ADR/0031-audit-retention-enforcement.md` — records D7–D10.

**Verify:** links from `ADR/README.md` valid.

---

## Task 16 — Final verification
- Clean run: `./mvnw -B --no-transfer-progress clean verify`.
- Manual: start app locally with Postgres+Redis; hit `/actuator/health/readiness`, `/actuator/health/liveness`, `/actuator/prometheus`. Curl an agent endpoint, confirm `X-Request-Id` in response header and in console log line.
- Confirm test-profile purge stays disabled (grep `application-test.yml`).
- `git status` clean of unrelated files; review diff.
- Do NOT commit or push — hand back to user for approval per M10 checkpoint.

---

## Rollback plan

Each task is one commit and independent enough to revert individually:
- Task 1 revert removes the Prometheus endpoint; no data loss.
- Tasks 3–5 revert removes MDC filter + orchestrator MDC push; log lines lose the two bracketed fields.
- Tasks 8–10 revert removes the scheduler and repo; retention becomes documented-only again (M9 state).
- Docs (Tasks 14–15) revert with the code they describe.

No schema migration means no down-migration to write.

---

## Sequencing summary

Tasks 1–2 (surface plumbing) → 3–5 (correlation) → 6 (missing timer) → 7 (cardinality guard) → 8–12 (retention) → 13 (E2E correlation) → 14–15 (docs+ADRs) → 16 (verify + stop).

Total commits expected: ~15 small, single-purpose. Total new production classes: 6. Total new dependencies: 1.
