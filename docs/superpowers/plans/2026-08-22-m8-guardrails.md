# M8 — Guardrails & Agent Safety Enforcement (Implementation Plan)

Spec: `docs/superpowers/specs/2026-08-22-m8-guardrails-design.md`. Execution: **inline TDD,
task-by-task**. New feature package `com.prince.agentic.guardrail`. No commits/pushes; leave
uncommitted. Coverage gate stays ≥0.75.

Each task: write test(s) → implement → compile/run the relevant suite green → move on.

---

## Task 1 — Configuration
- `GuardrailProperties` (`guardrail.*`: `confirmationTtlSeconds`, `userToolBudgetPerMin`,
  `maxArgumentChars`; zero→default) + `GuardrailPropertiesTest`.
- `GuardrailConfig` (`@EnableConfigurationProperties`).
- Wire `guardrail:` block into `application.yml`; keep `application-test.yml` defaults generous.

## Task 2 — Decision & outcome model
- `GuardrailOutcome` enum (`ALLOW`, `DENY`, `REQUIRE_CONFIRMATION`).
- `GuardrailDecision` record + factories (`allow`, `deny`, `requireConfirmation`) + test.
- `GuardrailContext` record (`executionId`, `requestId`).

## Task 3 — Policies + engine
- `GuardrailPolicy` interface (`evaluate(input)`, `order()`), `GuardrailInput` record
  (principal, decision, descriptor, ctx).
- `ArgumentSafetyPolicy` (order 10) + test: oversized/blatant-unsafe args → DENY; normal → ALLOW.
- `RiskPolicy` (order 20) + test: READ_ONLY/DETERMINISTIC → ALLOW; SIDE_EFFECTING/HIGH_RISK →
  REQUIRE_CONFIRMATION.
- `GuardrailEngine` interface + `DefaultGuardrailEngine` (resolve descriptor via `ToolRegistry`,
  ordered first-non-ALLOW-wins, unknown tool → ALLOW, metrics) + `DefaultGuardrailEngineTest`
  (incl. prompt/memory-safety: malicious text can't change risk/confirmation).

## Task 4 — Fingerprint
- `FingerprintService` (SHA-256 hex over canonical sorted-key JSON of userId, conversationId,
  toolName, canonicalArgs, riskLevel) + `FingerprintServiceTest` (stable; differs on any field).

## Task 5 — Confirmation exceptions
- `guardrail/exception/`: `GuardrailException(ApiException)` base +
  `ConfirmationNotFoundException` (404), `ConfirmationExpiredException` (410),
  `ConfirmationMismatchException` (409), `ConfirmationAlreadyUsedException` (409),
  `RateLimitedException` (429), `GuardrailDeniedException` (403) + a small exception test.

## Task 6 — Confirmation model + Redis store
- Records: `PendingAction` (tool, args, riskLevel), `Confirmation` (stored),
  `PendingConfirmation` (safe view), `ConfirmedAction`.
- `ConfirmationService` interface; `RedisConfirmationService` (`guard:confirmation:{id}`, TTL,
  JSON blob, owner-scoped, `getAndDelete` single-use, clock-checked expiry, fingerprint verify,
  metrics) mirroring `RedisConversationMemoryService`.
- Unit test with a fake/mocked `StringRedisTemplate` for logic; `RedisConfirmationIT`
  (Testcontainers) for create/confirm-once/replay/cross-user/expired/concurrent.

## Task 7 — Rate limiter
- `RateLimiter` interface (`tryAcquire(userId)`); `RedisRateLimiter`
  (`guard:rate:{userId}:{epochMinute}`, INCR/EXPIRE, injected `Clock`, budget from properties,
  metric) + `RedisRateLimiterIT` (below/at-over/reset/isolation).

## Task 8 — Agent-layer integration
- `AgentStatus`: add `PENDING_CONFIRMATION`, `BLOCKED`.
- `AgentResult`: add nullable `PendingAction pending`; update constructor/callers.
- `AgentOrchestrator`: inject `GuardrailEngine` + `RateLimiter`; gate before `ToolExecutor.execute`
  (deny→BLOCKED, confirm→PENDING_CONFIRMATION, allow→rate-limit→execute); pre-execution deadline
  check. Update `AgentOrchestratorTest` (mock engine/limiter; new confirm/deny/rate tests; keep
  read-only loop-mechanics tests).
- `AgentConfig`: provide `GuardrailEngine`/policies wiring if not component-scanned.

## Task 9 — Conversation + confirm services
- `ConversationOutcome`: add `PendingConfirmation` (nullable).
- `AgentConversationService`: on PENDING_CONFIRMATION create confirmation, best-effort append user
  message, return outcome. Update `AgentConversationServiceTest` + fakes.
- `AgentConfirmationService`: confirm → rate-limit → `ToolExecutor` → `AgentConfirmResponse`;
  metric `confirmation_approved`. Unit test (mock confirmation service + executor + limiter):
  exact action once, rate-limit blocks.

## Task 10 — API
- `AgentExecuteResponse`: add nullable confirmation fields (additive).
- `AgentConfirmResponse` DTO.
- `AgentController`: map PENDING_CONFIRMATION/BLOCKED on `/execute`; add
  `POST /confirmations/{id}`, `DELETE /confirmations/{id}` (Swagger, auth). Update
  `AgentControllerTest`.

## Task 11 — End-to-end IT
- `AgentGuardrailConfirmationIT` (Testcontainers Postgres+Redis, scripted LLM proposing
  `task.create`): execute → PENDING_CONFIRMATION → confirm → task created **exactly once**; replay →
  no second task.

## Task 12 — Docs + ADRs
- ADR-0021..0025.
- Update `GUARDRAILS.md`, `SECURITY.md`, `AGENT_ARCHITECTURE.md`, `TOOL_SYSTEM.md`, `MEMORY.md`,
  `OBSERVABILITY.md`, `TESTING.md`, `API.md`, `PERFORMANCE.md`, `DATA_PRIVACY.md`, `ROADMAP.md`,
  `TECH_STACK.md`, `CHANGELOG.md`, `AUDIT_LOGGING.md`, `README.md`, `backend/README.md`, `.env.example`.
- Mark **M8 IMPLEMENTED**, **M9 AUDIT PLANNED**, **M10 OBSERVABILITY DASHBOARDS PLANNED**. Never
  claim M8 solves all prompt injection.

## Task 13 — Verify
- `./mvnw clean test`, `./mvnw verify`; capture Surefire/Failsafe/JaCoCo. Security-review checklist.
- `git status` / `git diff --stat` / `git diff`. **Do not commit.** Stop with the M8 report.
