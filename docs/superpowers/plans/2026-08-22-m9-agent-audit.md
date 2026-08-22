# M9 — Durable Agent Audit & Execution History (Implementation Plan)

Spec: `docs/superpowers/specs/2026-08-22-m9-agent-audit-design.md`. Execution: **inline TDD,
task-by-task**. New feature package `com.prince.agentic.audit`. Flyway starts at **V5** (latest is V4).
No commits/pushes; coverage gate stays ≥0.75. Each task: failing test → implement → relevant suite green.

---

## Task 1 — Schema (Flyway V5)
- `V5__create_agent_audit.sql`: `agent_executions`, `agent_steps`, `tool_executions` (§3), with CHECKs,
  FKs (`ON DELETE CASCADE`), UNIQUE natural keys, and indexes. Portable SQL (PG + H2 PG-mode).
- `SchemaIT` / a new `AgentAuditSchemaIT` asserts tables/constraints/indexes exist (Testcontainers).

## Task 2 — Enums + entities + repositories
- Enums: `AuditExecutionStatus`, `AuditStepType`, `AuditStepStatus`, `AuditToolOutcome` (+ reuse
  `ToolRiskLevel`).
- Entities: `AgentExecutionRecord`, `AgentStepRecord`, `ToolExecutionRecord` (JPA, house style —
  `owner_id` column, `Instant`, `@Enumerated(STRING)`, surrogate PK + UNIQUE uid).
- Repositories: owner-scoped, paginated, nullable-filter queries (mirror `TaskRepository`):
  `findOwnedFiltered(ownerId, status, conversationId, from, to, toolName, pageable)`,
  `findByExecutionUidAndOwnerId`, step/tool finders by execution. Repository tests + `*IT`
  (persistence, UNIQUE constraints, pagination).

## Task 3 — Listener seam + event records (agent package)
- `AgentExecutionListener` interface + immutable event records (`AuditExecutionStart`, `AuditStep`,
  `AuditToolExecution`, `AuditExecutionEnd`) — no JPA.
- `NoOpAgentExecutionListener` default bean + test proving it no-ops. Boundary test still passes
  (agent package imports no repository/JPA).

## Task 4 — AuditService (audit module implements the listener)
- `AuditService implements AgentExecutionListener`: each method `@Transactional(REQUIRES_NEW)`,
  best-effort (catch persistence exception → WARN + `audit.write.failure`, never rethrow), idempotent
  (catch `DataIntegrityViolationException` → treat as recorded). Maps events → entities; enforces
  bounded summaries + `arguments_hash` (reuse M8 `FingerprintService` canonicalization).
- Unit tests (mock repos): create/step/tool/complete; idempotent double-insert; failure swallowed +
  metric; redaction (no raw content in any persisted field).

## Task 5 — Orchestrator integration (focused)
- Inject `AgentExecutionListener` into `AgentOrchestrator`; emit `onExecutionStarted` at run start,
  `onStep(LLM_DECISION/GUARDRAIL/TOOL_CALL/CONFIRMATION_REQUIRED/FINAL/FAILURE)` at existing points,
  `onToolExecution` around `ToolExecutor.execute`, `onExecutionCompleted` in `terminate`/`terminatePending`.
  Add per-step `System.nanoTime()` timings. Update `AgentOrchestratorTest` (inject a capturing fake
  listener; assert emitted lifecycle; existing behavior unchanged).

## Task 6 — Confirm-path audit + M8 additive thread
- Thread originating `executionId` into the M8 confirmation record + `ConfirmedAction` (additive; no
  behavior change). `AgentConfirmationService` emits `CONFIRMATION_APPROVED` step + `onToolExecution` +
  `onExecutionCompleted` (promote `PENDING_CONFIRMATION → COMPLETED/FAILED`); cancel/expiry emits
  `CONFIRMATION_REJECTED`. Update its unit test.

## Task 7 — Read API
- DTOs: `AgentExecutionSummary`, `AgentExecutionDetail` (+ `AgentStepView`, `ToolExecutionView`).
- `AgentAuditService`/query service (owner-scoped) + `AgentAuditController`:
  `GET /api/v1/agent/executions` (paginated, filtered), `GET /api/v1/agent/executions/{executionId}`.
  Swagger; sanitized (no internal names/raw content/secrets). Controller test: 200 shapes, filters,
  pagination, 404 masked (non-owner/missing), 401.

## Task 8 — End-to-end + security IT
- `AgentAuditE2EIT` (real Postgres+Redis, scripted LLM): agent run → audit rows exist & accurate;
  confirm flow appends approved step + tool_execution + promotes status. Cross-user 404; conversationId
  cannot cross ownership; API exposes no raw prompt/args/output/chain-of-thought/secret/stack trace.

## Task 9 — Config + observability
- `AuditProperties` (`audit.*`: `retentionDays` default 90, `finalResponseSummaryMaxChars` default 500,
  `resultSummaryMaxChars`); `AuditConfig`; yml + `.env.example` (`AGENT_AUDIT_RETENTION_DAYS`, …).
  `audit.*` metrics wired.

## Task 10 — Docs + ADRs
- ADR-0026…0029. Update `AUDIT_LOGGING.md`, `AGENT_ARCHITECTURE.md`, `SECURITY.md`, `DATA_PRIVACY.md`,
  `API.md`, `DATABASE.md` (supersede `audit_events` sketch), `TESTING.md`, `OBSERVABILITY.md`,
  `PERFORMANCE.md`, `ROADMAP.md`, `TECH_STACK.md` (no new deps), `CHANGELOG.md`, `README.md`,
  `backend/README.md`. Mark **M9 IMPLEMENTED**, **M10 DASHBOARDS PLANNED**.

## Task 11 — Verify
- `./mvnw clean test`, `./mvnw verify`; capture Surefire/Failsafe/JaCoCo; **measure** audit-write time.
  Security-review checklist. `git status` / `git diff --stat` / `git diff`. **Do not commit.** Stop with
  the M9 report.
