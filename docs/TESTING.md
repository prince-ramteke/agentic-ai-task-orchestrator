# Testing Strategy
## Agentic AI Task Orchestrator

> Tests are added with each milestone. Deterministic, no live network/LLM.

> **Milestone 1 status (VERIFIED 2026-08-21):** 8 tests — context load (`AgenticApplicationTests`), health endpoint (`HealthControllerTest`), error-handler mapping via standalone MockMvc (`GlobalExceptionHandlerTest`), and config/version-filtering sanity (`ApplicationConfigTest`).

> **Milestone 2 status (VERIFIED 2026-08-21):** **39 tests total, all green** under `./mvnw verify`. Security suite: `AuthIntegrationTest` (20, `@SpringBootTest` + MockMvc through the real filter chain — register/login/JWT/RBAC/error-envelopes/password-hash/DB-constraint), `AuthHttpSocketTest` (3, `RANDOM_PORT` + `TestRestTemplate` — **real socket-level HTTP** over embedded Tomcat), `JwtServiceTest` (4 — roundtrip, expired, malformed, forged signature), `AuthorizationServiceTest` (4 — owner/admin/other/null ownership). Tests run against **H2 in PostgreSQL-compat mode executing the production Flyway migrations** — reproducible without Docker (ADR-0005). Testcontainers-PostgreSQL integration is deferred to M3.

> **Milestone 5 status (VERIFIED 2026-08-21):** the tool framework adds deterministic, fast tests
> (no Ollama, mostly no Spring context): `ToolDescriptorTest`, `ToolExceptionTest`, `ToolRegistryTest`
> (registration, duplicate/invalid-role fail-fast, immutable sorted view), `ToolExecutorTest` (the
> full gate matrix: not-found/unauthorized/forbidden/invalid-input/unknown-property/domain-error/
> unexpected), `AbstractToolContractTest` (reusable contract subclassed by every tool),
> `ExpressionEvaluatorTest` + `CalculatorToolTest` (arithmetic, precedence, and rejection of
> divide-by-zero, letters, and code-like input), and per-tool task/customer tests (mock the domain
> service, assert the context principal is passed). Full-context tests on H2: `ToolSecurityTest`
> (ownership 404-masking, admin-any-by-id, spoofed-owner rejected, anonymous unauthorized, destructive
> tools not registered, all six registered) and `ToolCatalogApiTest` (`GET /api/v1/tools` — 200 ADMIN
> metadata-only, 403 USER, 401 anon). `ToolArchitectureBoundaryTest` enforces no `ai.*`/persistence
> imports. Coverage gate held; `tool/api/**` excluded (thin controller/DTOs).
>
> **Milestone 4 status (VERIFIED 2026-08-21):** **143 fast tests** (surefire; +44 for the AI layer),
> all green under `./mvnw verify` with the coverage gate held (~88%). AI tests are deterministic and
> **never touch a live model**: `AiServiceTest` (text/structured/validation-repair/invalid-output/
> provider paths with a mocked `LlmClient`), `AiControllerTest` (200/400/401 and the 502/503/504/422
> LLM-error envelope, `@MockitoBean AiService`), `AiIntegrationTest` (full `@SpringBootTest` context
> with a `@Primary FakeLlmClient` — proves the app **boots and serves AI with Ollama absent**),
> `PromptServiceTest`, `AiDtoValidationTest`, `FakeLlmClientTest`, `LlmExceptionTest`,
> `OllamaLlmClientTest` (transport → `LlmException` mapping), and `ArchitectureBoundaryTest` (the AI
> package must not reference the Task/Customer domains or persistence, and only `ai.llm.ollama`/
> `ai.config` may import Spring AI). A **gated live Ollama IT** (`OllamaLlmClientLiveIT`) is **skipped**
> in normal `verify`; run it explicitly against a running Ollama:
> `./mvnw -Dllm.live.ollama=true -Dit.test=OllamaLlmClientLiveIT verify` (needs `ollama serve` +
> `ollama pull llama3.2`). It was run for M4 (**3/3 PASS** against real `llama3.2`) — live model
> VERIFIED. JaCoCo excludes now also cover `ai/config/**` and `ai/llm/ollama/**` (provider/infra).
>
> **Milestone 3 status (VERIFIED 2026-08-21):** **99 fast tests** (surefire, H2 in PostgreSQL-mode)
> plus **Testcontainers PostgreSQL integration tests** (`*IT`, failsafe). Fast suite adds unit tests
> (`SortWhitelistTest`, `Task`/`Customer` mapper + service with Mockito) and `@SpringBootTest`+MockMvc
> API tests (`TaskApiTest` 17, `CustomerApiTest` 10 — CRUD, validation 400, 401, ownership 404-masking,
> admin-any-by-id, admin own-list, mass-assignment rejection, pagination/filter/sort, customer 409).
> Integration tests (`SchemaIT`, `TaskPersistenceIT`, `CustomerPersistenceIT`) run the real Flyway
> migrations on `postgres:16-alpine` and assert CHECK/UNIQUE constraints and FK cascade — they run
> for real when Docker is present (a single shared `postgres:16-alpine` — the Testcontainers
> singleton-container pattern, so the cached Spring test context stays valid across IT classes) and
> **skip cleanly** via a Docker-availability assumption otherwise, so `./mvnw verify` stays green
> without Docker. They caught a PostgreSQL-only bug that H2
> had tolerated (nullable `String` param → `lower(bytea)`), fixed with `CAST(:search AS string)`.
> The **JaCoCo enforcement gate is now active** (see §8). The failsafe plugin pins Docker
> `api.version=1.44` (docker-java in Testcontainers 1.20.x otherwise fails against Docker Engine 29
> with HTTP 400). ITs are required in Docker-capable CI; PostgreSQL integration is only claimed
> *verified* when the ITs actually ran.

## 1. Test pyramid

```
        E2E (few)            request -> agent -> tools -> result
      Integration           @SpringBootTest + Testcontainers (Postgres, Redis, API)
   Unit (many)              services, tools, validators, security, mappers
   Evaluation (parallel)    agent behavior vs. dataset (EVALUATION.md)
```

## 2. Unit tests

- JUnit 5 + Mockito. Mock the LLM provider, tool dependencies, and repositories.
- Cover: business rules, validators, mappers, security/authorization logic, and each tool (happy path, argument-validation failure, authorization refusal, audit produced for side-effecting, confirmation-required for high-risk).
- Real assertions; Arrange–Act–Assert; `method_condition_expected`. Never "asserts no exception".

## 3. Agent / AI tests (deterministic)

Use `FakeLlmClient` and fake tools to make the loop deterministic. Cover:
- Tool selection over a scripted decision sequence.
- Argument validation and rejection of bad model arguments.
- Authorization refusal (agent cannot exceed user permissions).
- Multi-step workflow (e.g. search → calculate → create).
- Repair/retry on malformed output.
- Guardrail bounds tripping (max calls, timeout, retry cap, loop detection).
- Confirmation flow for dangerous ops.
- Prompt-injection resistance (`THREAT_MODEL.md`).

## 4. Integration tests

- `@SpringBootTest` + Testcontainers: real Postgres and Redis; real HTTP via MockMvc/WebTestClient.
- Cover: endpoints (happy + 400 + 401/403 + 422), CRUD with ownership, Flyway migrations applying, Redis TTL/state behavior, and durable execution-record persistence.
- Naming: `XxxIT`.

## 5. End-to-end tests

The full path: authenticated request → orchestrator → tools → domain → Postgres → response + execution record. At least the flagship multi-step objective (U3) runs end-to-end against the deterministic fake model.

## 6. Evaluation suite

Runs in parallel to the pyramid; scores agent behavior against the dataset (`EVALUATION.md`). CI-runnable with a pinned/mocked model.

## 7. Error/edge coverage (required)

Invalid input (400), unauthenticated (401), not found / **not owner → 404** (existence-masking on owned domain resources; 403 is reserved for RBAC/role denial such as a USER hitting an ADMIN-only route), malformed model output (422), guardrail budget (429). Security tests for injection and authorization refusal.

## 8. Coverage gate (targets)

- Service/domain logic: **≥ 80%**.
- Overall: **≥ 75%**.
- Tools and guardrails: every branch of validation/authorization/bounds covered.
- **Status:** the **enforcement gate is ACTIVE as of M3** — `jacoco:check` runs in `./mvnw verify` and fails the build below **75% BUNDLE instruction coverage** (excludes: bootstrap class, `config`, DTO records, response envelopes). Coverage is measured from the surefire (`*Test`) suite, so the gate holds even when Docker-gated `*IT` tests are skipped. Current overall ≈ 88%; `TaskService`/`CustomerService` ≥ 80% (report-verifiable). Do not drop below the gate, and do not add assertion-free tests to inflate it.

## 9. Rules

- **Never** call a live LLM or external network in a test.
- **Never** mark work done with failing/partial tests.
- **Never** write a test that only asserts no-exception.
- Run `./mvnw verify` green before pushing (`DEFINITION_OF_DONE.md`).

## Milestone 6 — Agent test strategy (IMPLEMENTED)

Three levels, all deterministic and offline (`./mvnw verify` needs **no** live Ollama):
- **Unit:** `AgentDecisionValidatorTest`, `LoopDetectorTest`, `DeadlineCancellationTokenTest`, `AgentExecutionTest`, `ObservationSerializerTest`, `AgentToolCatalogTest`, `AgentPropertiesTest`, `AgentPlannerTest`, `AgentExceptionTest`.
- **Orchestrator:** `AgentOrchestratorTest` drives the real `AgentOrchestrator` over a `ScriptedLlmClient` (a sequence-aware `LlmClient` test double) — direct FINAL, single/multi tool call, invalid-decision repair (success + failure), tool-not-found/unauthorized/failure observations, iteration/tool-call limits, loop detection, timeout (advancing `Clock`), cancellation (external token), and side-effect-retry safety.
- **Integration:** `AgentExecuteIT` (`@SpringBootTest` + Testcontainers Postgres, `ScriptedLlmClient` as `@Primary`) exercises the authenticated endpoint → real orchestrator → real `ToolExecutor` → real `TaskService` → real DB, with the security cases (own-data, cross-user 404, admin any-by-id, identity-spoof ignored, unregistered tool not executed, 401).
- **Boundary:** `AgentArchitectureBoundaryTest` scans `agent.*` for forbidden imports (repository/`EntityManager`/`JdbcTemplate`/domain services/Spring AI).

## Milestone 7 — Memory tests (IMPLEMENTED)

- **Unit:** `MemoryPropertiesTest`, `MemoryBoundsTest` (deterministic trim/render, both bounds),
  `RedisConversationMemoryServiceTest` (mocked `StringRedisTemplate`: round-trip, owner-mismatch/
  malformed-blob masked 404, Redis-down → 503, sliding-TTL write, trim, delete),
  `AgentConversationServiceTest` (hybrid policy, history-passing, degrade), `MemoryArchitectureBoundaryTest`
  (no Spring AI / tools / domain / persistence in the memory package). `FakeConversationMemoryService`
  is the deterministic double for agent/service tests.
- **Redis integration (Testcontainers `redis:7-alpine`, Docker-skip):** `RedisConversationMemoryIT` —
  append/load round-trip, user-scoped key, storage trim, sliding-TTL set, **real expiration** (short TTL
  + Awaitility), delete, and **cross-user 404 isolation**. The shared IT base now starts both PostgreSQL
  and Redis so the `it` profile mirrors production.
- **Agent multi-turn (`AgentConversationIT`, real Redis + Postgres + `ScriptedLlmClient`):** proves
  turn 2's captured LLM prompt **contains turn 1's bounded context** (not merely that Redis holds data),
  plus cross-user 404, backward-compat (no `conversationId`), and delete-then-reuse → 404.
- Live Ollama tests stay profile-gated. `./mvnw verify` runs the Redis IT for real when Docker is present.

## Milestone 9 — Durable audit tests (IMPLEMENTED)

- **Unit:** `AuditServiceTest` (best-effort swallow + metrics; failure never rethrown), `AuditWriterTest`
  (idempotent check-before-insert, summary bounding/redaction, status mapping, skip-on-missing),
  `NoOpAgentExecutionListenerTest` (default sink truly no-ops), `AgentOrchestratorTest` (lifecycle
  emission via a capturing listener; existing behaviour unchanged).
- **Integration (Testcontainers PostgreSQL):** `AgentAuditPersistenceIT` (round-trip, UNIQUE natural-key
  idempotency, owner-scoped + paginated + date-range filtering, confirm-promotion).
- **End-to-end (Postgres + Redis, scripted LLM):** `AgentAuditE2EIT` — agent → guardrail → tool → audit
  → PostgreSQL, read back via the API; the confirm flow appends a `CONFIRMATION_APPROVED` step + tool
  execution and promotes the execution; cross-user reads are masked 404.
- **Security:** `AgentAuditControllerTest` + `AgentAuditE2EIT` assert owner isolation and that no raw
  prompt/argument/output/chain-of-thought/secret is stored or returned. Coverage gate ≥0.75 held;
  live-Ollama tests stay profile-gated.

## Milestone 10 — Observability & retention tests (IMPLEMENTED)

- **Unit:** `RequestIdFilterTest` (UUID validation, MDC set/clear, junk header replaced, MDC
  cleared even on downstream throw); `AuditRetentionPropertiesTest` (defaults, blank-cron
  normalization); `AuditRetentionJobTest` (disabled flag → noop, loop until zero, `maxBatches`
  cap, best-effort batch failure short-circuits without rethrow, `ReentrantLock.tryLock()`
  overlap-skip proven with a blocking anonymous repo).
- **App-context (`test` profile, MockMvc):** `ObservabilityEndpointsTest` — `/actuator/prometheus`
  is anonymously reachable and returns Micrometer text output (`jvm_*`); `X-Request-Id` is minted
  when missing, echoed exactly for a valid UUID, and replaced for junk. Readiness/liveness probes
  are exposed. `MetricCardinalityTest` walks the whole `MeterRegistry` after boot and asserts no
  meter carries a forbidden tag key (ADR-0030 cardinality rule).
- **Integration (Testcontainers PostgreSQL):** `AuditRetentionIT` seeds 3 old + 2 fresh
  `agent_executions` with children via raw JDBC; calls `AuditRetentionJob.runOnce()`; asserts
  the three old parents and their cascaded `agent_steps` / `tool_executions` are gone; asserts
  fresh parents and their children survive; asserts `retention.purge.deleted{table=…}` incremented
  by exactly 3; a second invocation is a no-op.
- **CI compatibility:** the scheduler is disabled in the `test` profile
  (`audit.purge.enabled: false`) so surefire runs deterministically; the `it` profile keeps it
  enabled (matches production) — the nightly cron will not fire during a short test run.

