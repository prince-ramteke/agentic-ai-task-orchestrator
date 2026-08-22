# Changelog
## Agentic AI Task Orchestrator

All notable changes to this project are recorded here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/). Do not fabricate history — add an entry only for a change that actually happened.

Categories: **Added · Changed · Fixed · Removed · Security · Docs**.

---

## [Unreleased]

_Next: Milestone 10 — Observability dashboards (Prometheus/Grafana), retention purge enforcement._

---

## [0.0.9] — 2026-08-22 — Milestone 9: Durable Agent Audit & Execution History

### Added
- **Audit module** (`com.prince.agentic.audit`): Flyway `V5` three typed tables — `agent_executions`, `agent_steps`, `tool_executions` — with `BIGINT` identity PKs + UUID natural keys (`execution_uid`/`tool_execution_uid`), `owner_id` FK CASCADE, `TIMESTAMPTZ`→`Instant`, VARCHAR enums + CHECK constraints, and query-pattern indexes. JPA entities + repositories.
- **Listener seam** (agent package, repository-free): `AgentExecutionListener` (+ event records `AuditExecutionStart`/`AuditStepEvent`/`AuditToolEvent`/`AuditExecutionEnd`/`AuditConfirmationExecuted`), `NoOpAgentExecutionListener` default, and a stateless `AgentAuditEmitter` that builds events + computes `arguments_hash` (SHA-256, reusing the M8 `FingerprintService` canonicalization). New agent-native enums `AgentStepKind`/`AgentStepOutcome`/`AgentToolOutcome`.
- **Recorder:** best-effort `AuditService` implementing the listener (`@Primary`) delegating to a transactional `AuditWriter` (`REQUIRES_NEW`, idempotent via UNIQUE natural keys, swallow-on-failure + `audit.write.failure`). Never blocks or rolls back the agent/domain path.
- **Read API:** `GET /api/v1/agent/executions` (paginated, filtered via type-safe JPA Specifications) and `GET /api/v1/agent/executions/{executionId}` (steps + tool executions); owner-scoped, masked 404, sanitized DTOs. `AuditProperties` (`audit.*`), `AuditConfig`.
- **Metrics:** `audit.execution.created`, `audit.step.created`, `audit.tool_execution.created`, `audit.write.success`, `audit.write.failure`.

### Changed
- `AgentOrchestrator` emits audit lifecycle facts at each step (start / LLM decision / guardrail / tool / confirmation-required / final / failure / completion) via `AgentAuditEmitter`; gains a `conversationId` run parameter for audit correlation. `AgentConfirmationService` emits a `CONFIRMATION_APPROVED` step + tool execution and promotes the run `PENDING_CONFIRMATION → COMPLETED/FAILED`.
- **Additive M8 propagation (no behavior change):** `PendingAction`/`Confirmation`/`ConfirmedAction` carry the originating `executionId` so a confirm is audited against the correct run; `GuardrailDecision.allowWithRisk` lets the engine surface the resolved risk on ALLOW for tool audit.

### Security / Privacy
- Persists safe metadata + hashes + bounded, length-capped summaries only — **never** raw prompts, tool arguments, LLM output, system prompts, **chain-of-thought**, JWTs, or secrets. Reads are owner-scoped (cross-user → masked 404); `conversationId` is a filter, never an authorization claim. Honest best-effort semantics: a business action can succeed while its audit row is temporarily missing (recorded as a metric), documented.

### Docs
- ADR-0026…0029; updated `AUDIT_LOGGING.md` (owner-scoped; supersedes the admin-all implication), `DATABASE.md` (supersedes the `audit_events` sketch with the typed 3-table model), `AGENT_ARCHITECTURE.md`, `SECURITY.md`, `DATA_PRIVACY.md`, `API.md`, `TESTING.md`, `OBSERVABILITY.md`, `PERFORMANCE.md`, `ROADMAP.md`, `TECH_STACK.md`, `README.md`, `backend/README.md`, `.env.example`. M10 dashboards / retention purge marked PLANNED.

---

## [0.0.8] — 2026-08-22 — Milestone 8: Guardrails & Agent Safety Enforcement

### Added
- **Guardrail module** (`com.prince.agentic.guardrail`): backend-authoritative `GuardrailEngine` between the validated `AgentDecision` and `ToolExecutor`, returning `ALLOW` / `DENY` / `REQUIRE_CONFIRMATION`. Ordered, pure `GuardrailPolicy` beans (first-non-ALLOW wins): `RiskPolicy` (descriptor-authoritative — READ_ONLY/DETERMINISTIC → ALLOW, SIDE_EFFECTING/HIGH_RISK → REQUIRE_CONFIRMATION; the model cannot downgrade risk) and `ArgumentSafetyPolicy` (size cap + blatant-marker deny-list, a documented modest heuristic — not a prompt-injection solution). `GuardrailProperties` (`guardrail.*`), `GuardrailConfig`.
- **Confirmation** (`guardrail.confirmation`): `FingerprintService` (SHA-256 over userId+conversationId+toolName+canonical args+riskLevel); Redis-backed single-use `RedisConfirmationService` (`guard:confirmation:{id}`, TTL `AGENT_CONFIRMATION_TTL_SECONDS`, atomic `GETDEL`, owner-scoped, clock-checked expiry, fingerprint verify); records `PendingAction`/`Confirmation`/`PendingConfirmation`/`ConfirmedAction`.
- **Rate limiting:** `RedisRateLimiter` — per-user fixed window `guard:rate:{userId}:{epochMinute}` (`INCR`/`EXPIRE`), budget `AGENT_USER_TOOL_BUDGET_PER_MIN`; consumed only on actual execution.
- **Agent integration:** new `AgentStatus` terminals `PENDING_CONFIRMATION` / `BLOCKED`; `AgentResult.pending`; orchestrator gate before `ToolExecutor` (deny → BLOCKED, confirm → halt, allow → rate-limit → execute); `AgentConfirmationService` executes the exact stored action **exactly once**; `AgentConversationService` creates the fingerprint-bound confirmation.
- **API:** `POST /api/v1/agent/confirmations/{id}` (confirm & execute, no body), `DELETE /api/v1/agent/confirmations/{id}` (cancel); `/execute` may now return `PENDING_CONFIRMATION` (+ safe `confirmation*` fields) or `BLOCKED` — additive, published shape preserved.
- **Metrics:** `guardrail.{allow,deny,confirmation_required,confirmation_approved,confirmation_expired,rate_limited,policy_violation}` (low-cardinality labels only).

### Changed
- `AgentOrchestrator` now depends on `GuardrailEngine` + `RateLimiter`; no effect can occur before guardrail evaluation. Timeouts remain **layered** (LLM-provider + cooperative deadline + per-tool pre-execution check) and never force-cancel an in-flight write.

### Security
- Confirmation is single-use, owner-scoped, fingerprint-bound, replay/mutation/expiry-resistant; a foreign id is masked 404 and never consumes another user's action. Risk is descriptor-driven and the model cannot escalate; identity is always the verified principal. Prompt/memory-safety tests prove untrusted text cannot change risk/role/confirmation/identity. **Not a claim to solve all prompt injection** — the boundary is structural.

### Docs
- ADR-0021…0025; updated `GUARDRAILS.md`, `SECURITY.md`, `AGENT_ARCHITECTURE.md`, `TOOL_SYSTEM.md`, `MEMORY.md`, `OBSERVABILITY.md`, `API.md`, `PERFORMANCE.md`, `DATA_PRIVACY.md`, `TESTING.md`, `TECH_STACK.md`, `AUDIT_LOGGING.md`, `README.md`, `.env.example`. M9 audit / M10 dashboards marked PLANNED.

---

## [0.0.7] — 2026-08-22 — Milestone 7: Redis Conversation Memory

### Added
- **Memory module** (`com.prince.agentic.memory`): Redis-backed short-term conversation memory, isolated from Spring AI and never accessed by tools. `ConversationMemoryService` abstraction + `RedisConversationMemoryService` (Spring Data Redis / Lettuce, `StringRedisTemplate`); records `ConversationMemory` / `MemoryMessage` / `MemoryRole {USER, ASSISTANT, TOOL}`; pure `MemoryBounds` (deterministic latest-message trimming); `MemoryProperties` (`agent.memory.*`); `MemoryConfig`; exceptions `ConversationNotFoundException` (404), `MemoryUnavailableException` (503).
- **Storage:** one application-owned JSON blob per conversation under `conv:{userId}:{conversationId}` (server-minted UUIDv4). No Java native serialization, no class-name polymorphic storage; `schemaVersion=1`. Stores user/assistant text + bounded TOOL summaries only — never entities, tokens, or security context.
- **Bounds & TTL:** storage bound 50 msgs / 12,000 chars; smaller LLM-context bound 12 msgs / 6,000 chars (full history never sent to the model); sliding 24h TTL refreshed each turn. All env-tunable via `AGENT_MEMORY_TTL_SECONDS`/`_MAX_MESSAGES`/`_MAX_CHARS`/`_CONTEXT_MAX_MESSAGES`/`_CONTEXT_MAX_CHARS`.
- **M6 integration:** `AgentConversationService` wraps the still-Redis-free `AgentOrchestrator` (load bounded history → run → append bounded turn → refresh TTL). New delimited `{history}` slot in `agent-system.st` (untrusted context, not instructions); `AgentPlanner.decide`/`AgentPromptService.render`/`AgentOrchestrator.run` gain a history parameter (stateless M6 entry points preserved).
- **API (additive, non-breaking):** `POST /api/v1/agent/execute` accepts an optional UUID `conversationId` (absent → new conversation) and returns `conversationId` + `memoryStatus` (`ACTIVE`/`UNAVAILABLE`). New `DELETE /api/v1/agent/conversations/{id}` → 204 (ownership-checked, 404-masked). No conversation-list endpoint.
- **Metrics:** `memory.load`, `memory.append`, `memory.trim`, `memory.hit`, `memory.miss`, `memory.unavailable`, `agent.conversation` (tagged `memoryStatus`). No raw content or ids in labels/logs.
- **Tests:** unit (`MemoryPropertiesTest`, `MemoryBoundsTest`, `RedisConversationMemoryServiceTest` over a mocked template incl. malformed-blob/owner-mismatch/unavailable, `AgentConversationServiceTest`, `MemoryArchitectureBoundaryTest`, `FakeConversationMemoryService`); real-Redis Testcontainers (`redis:7-alpine`) `RedisConversationMemoryIT` (round-trip, trim, sliding TTL, real expiration, delete, cross-user isolation, user-scoped key); multi-turn `AgentConversationIT` proving turn 2's prompt contains turn 1's bounded context, plus cross-user 404, backward-compat, and delete-then-reuse. 296 unit + 26 IT (3 live-Ollama skipped) green; overall instruction coverage 93%.

### Changed
- `AgentResult` carries the run's bounded observations (internal; never exposed by the API DTO) so memory can persist bounded TOOL turns.
- The shared IT base starts both PostgreSQL and Redis containers, so the `it` profile mirrors production and `/actuator/health` reports UP. The infra-free `test` profile disables only the Redis health indicator (the default indicator stays on in every real profile).

### Security
- Conversation ownership enforced server-side two ways (userId-scoped key + asserted stored owner); server-minted UUIDs; missing/expired/foreign id → masked 404. Memory content is untrusted and confined to the delimited `{history}` slot — never a replacement system prompt. Identity always from the authenticated principal, never from `conversationId` or the body.

### Docs
- ADR-0017 (Redis conversation memory architecture), ADR-0018 (retention & bounding), ADR-0019 (failure semantics), ADR-0020 (ownership & isolation). Updated `MEMORY.md`, `API.md`, `AGENT_ARCHITECTURE.md`, `SECURITY.md`, `DATA_PRIVACY.md`, `OBSERVABILITY.md`, `PERFORMANCE.md`, `TESTING.md`, `DEPLOYMENT.md`, `TECH_STACK.md`, `GUARDRAILS.md`, `AUDIT_LOGGING.md`, `ROADMAP.md`, `.env.example`, `README.md`, `backend/README.md`.

---

## [0.0.6] — 2026-08-21 — Milestone 6: Agent Orchestration

### Added
- **Agent layer** (`com.prince.agentic.agent`): `AgentOrchestrator` — a bounded, backend-controlled loop that turns one authenticated request into a sequence of registered-tool executions (decision → validate → execute → observe → repeat until `FINAL` or a bound trips). The model proposes; the backend disposes.
- **Typed decision contract:** `AgentDecision(action, response, tool, arguments)` / `AgentAction {FINAL, TOOL_CALL}`, produced via the existing M4 `LlmClient.generateStructured` (no M4 change) with one bounded repair; `AgentDecisionValidator` rejects malformed combinations.
- **Tool catalog adapter:** `AgentToolCatalog` derives model-facing tool definitions **reflectively from the M5 `ToolRegistry`** (name, description, category, risk, input fields + enum values) — no hardcoded tool list, no Spring AI in the tool layer.
- **Bounded observations:** `AgentObservation` + `ObservationSerializer` (caps chars and array items; `PageResponse` shaped) — a raw `ToolResult` is never fed to the prompt.
- **Cooperative bounds** (checked between steps): `AgentProperties` — max iterations (`AGENT_MAX_ITERATIONS`=8), max tool calls (`AGENT_MAX_TOOL_CALLS`=10), one shared wall-clock deadline (`AGENT_TIMEOUT_SECONDS`=60, computed once), `CancellationToken`/`DeadlineCancellationToken`, and `LoopDetector` (fingerprint = tool + canonical args, `AGENT_LOOP_THRESHOLD`=2). No unbounded execution path.
- **Endpoint** `POST /api/v1/agent/execute` (authenticated, deny-by-default) → `AgentExecuteResponse{executionId, status, response, iterations, toolCalls, durationMs, failureCode?}`. Started runs that terminate in a controlled state return **200** with a stable `failureCode`; only pre-execution request/auth faults use the `ApiError` envelope. Request body carries only `message` (≤4000).
- **Prompt:** versioned `resources/prompts/agent-system.st`, rendered by `AgentPromptService` with untrusted text (user message, observations) only in delimited slots.
- **Metrics** (orchestration-level only, no double-counting M5): `agent.execution.duration`/`agent.execution.count` (tag status), `agent.iterations`, `agent.tool.calls`, `agent.loop.detected`, `agent.limit.reached`.
- **Tests:** unit (`AgentDecisionValidatorTest`, `LoopDetectorTest`, `DeadlineCancellationTokenTest`, `AgentExecutionTest`, `ObservationSerializerTest`, `AgentToolCatalogTest`, `AgentPropertiesTest`, `AgentPlannerTest`, `AgentExceptionTest`), orchestrator scenarios over a deterministic `ScriptedLlmClient` (`AgentOrchestratorTest` — direct FINAL, single/multi tool call, invalid-decision repair, tool-not-found/unauthorized/failure observations, iteration/tool-call limits, loop detection, timeout, cancellation, side-effect-retry safety), the full Testcontainers-Postgres `AgentExecuteIT` (own-data, cross-user 404, admin any-by-id, identity-spoof ignored, unregistered tool not executed, 401), `AgentControllerTest`, and `AgentArchitectureBoundaryTest` (agent.* imports no repository/`EntityManager`/`JdbcTemplate`/domain service/Spring AI — only the `ai.llm` abstraction). `verify` needs no live Ollama.

### Changed
- `docs/GUARDRAILS.md`, `docs/ROADMAP.md`, `docs/AGENT_ARCHITECTURE.md`, `docs/API.md`: record the M6 cooperative-bounds / M8 hard-enforcement split and the `POST /api/v1/agent/execute` endpoint (superseding the `/api/agent/chat` placeholder).

### Security
- The LLM remains an untrusted planner: identity is always the authenticated principal (never model/body-supplied), every effect flows through the M5 `ToolExecutor` (role + ownership authorization preserved), unregistered tool names cannot execute, and side-effecting tools are never auto-retried.

### Docs
- ADR-0013 (agent decision contract), ADR-0014 (execution loop & cooperative budgets), ADR-0015 (agent/tool orchestration boundary), ADR-0016 (loop detection).

---

## [0.0.5] — 2026-08-21 — Milestone 5: Tool Registry & Tool Execution Framework

### Added
- **Tool framework** (`com.prince.agentic.tool`): `Tool<I,O>` (handler returns raw `O`), `ToolDescriptor` (name, description, category, version, `ToolRiskLevel`, requiresAuthentication, requiredRoles, input/output `Class`, timeout — compact-ctor validated), `ToolRiskLevel` (`READ_ONLY/DETERMINISTIC/SIDE_EFFECTING/HIGH_RISK`), `ToolExecutionContext` (identity from the authenticated principal; backend-built, never from arguments), `ToolResult<O>` + `ToolError` envelope.
- **`ToolRegistry`** — fail-fast (duplicate name / invalid role / null handler → `ToolRegistrationException`, fails boot), immutable after startup, O(1) lookup, name-sorted descriptor view.
- **`ToolExecutor`** — ordered gates `resolve → authenticate → authorize (role, any-of) → bind → validate → execute → wrap`; unknown argument properties rejected (`FAIL_ON_UNKNOWN_PROPERTIES`); domain `ApiException` surfaced with its code; `tool.execution.duration`/`tool.execution.result` Micrometer metrics; metadata-only logging.
- **Tool exception model** (`TOOL_NOT_FOUND`/`TOOL_INVALID_INPUT`/`TOOL_UNAUTHORIZED`/`TOOL_FORBIDDEN`/`TOOL_TIMEOUT`/`TOOL_EXECUTION_FAILED` via `ApiException`; `TOOL_REGISTRATION_ERROR` fails boot).
- **Six tools** (one class each): `task.get`, `task.search`, `task.create` (reuses `TaskCreateRequest`, no ownerId), `customer.get`, `customer.search`, `math.calculate`. Domain tools wrap `TaskService`/`CustomerService` and pass the context principal — ownership/404-masking/admin-any-by-id inherited. `math.calculate` uses a safe recursive-descent `ExpressionEvaluator` (`+ - * / ()`, decimals, unary minus) — **no** `ScriptEngine`/`eval`/`Runtime.exec`/`ProcessBuilder`.
- **ADMIN read-only `GET /api/v1/tools`** — descriptor metadata only (no implementation class names).
- **Tests:** `ToolDescriptorTest`, `ToolExceptionTest`, `ToolRegistryTest`, `ToolExecutorTest` (gate matrix), `AbstractToolContractTest` (reusable contract, subclassed per tool), `ExpressionEvaluatorTest`, `CalculatorToolTest`, task/customer tool tests, `ToolSecurityTest` (full-context ownership/role IT on H2), `ToolCatalogApiTest`, `ToolArchitectureBoundaryTest` (no `ai.*`/persistence imports).

### Changed
- `pom.xml`: JaCoCo excludes += `com/prince/agentic/tool/api/**` (thin controller/DTOs). **No new dependencies** — the framework uses existing Spring/Jackson/Micrometer. Spring Boot stays 3.4.1.

### Security
- Identity is backend-supplied via `ToolExecutionContext`; the model can never manufacture `userId`/`roles`. Two authorization layers: role (any-of, in the executor) + resource ownership (in the domain service). Fail-closed ordered gates; bounded inputs; safe calculator; ADMIN-only catalog. The tool subsystem imports no Spring AI. (ADR-0012.)

### Docs
- ADR-0011 (tool abstraction & registry), ADR-0012 (tool authorization & execution-context boundary); updated `TOOL_SYSTEM`, `AGENT_ARCHITECTURE`, `SECURITY`, `GUARDRAILS`, `EVALUATION`, `OBSERVABILITY`, `API`, `TESTING`, `PERFORMANCE`, `TECH_STACK`, `ROADMAP`, `README`, `backend/README`. Reconciled two prior doc statements: risk-enum names and dot-namespaced tool names.

---

## [0.0.4] — 2026-08-21 — Milestone 4: Spring AI + Ollama Integration

### Added
- **LLM provider abstraction** (`ai.llm` package): `LlmClient` interface (`generate`, `generateStructured`, `info`) — the single, provider-agnostic path to the model — and `LlmProviderInfo` (project-owned metadata record). `OllamaLlmClient` (the only class importing `org.springframework.ai.*`) implements it via **Spring AI 1.0.9** over local **Ollama**; `FakeLlmClient` (test) implements it deterministically with no network.
- **AI application layer** (`ai` package): `AiService` (prompt → LlmClient → Bean-Validation of structured output → bounded one-repair → application DTOs; metadata-only logging + Micrometer metrics), `PromptService` + versioned templates (`resources/prompts/generate.st`, `classify.st`, untrusted input delimited), `AiController` with authenticated `POST /api/v1/ai/generate` and `POST /api/v1/ai/classify`.
- **AI exception model**: `LlmException extends ApiException` + `LlmUnavailableException` (503 `LLM_UNAVAILABLE`), `LlmTimeoutException` (504 `LLM_TIMEOUT`), `LlmProviderException` (502 `LLM_PROVIDER_ERROR`), `LlmInvalidOutputException` (422 `LLM_INVALID_OUTPUT`) — rendered by the existing `GlobalExceptionHandler`, no new handler.
- **Structured output** via Spring AI's converter into `AiClassificationResult{category,priority,summary}`, re-validated as untrusted; API response `AiClassificationResponse` adds server-supplied `model`/`provider`.
- **Config**: `spring-ai-bom` 1.0.9 import + `spring-ai-starter-model-ollama`; `LlmProperties` + `AiConfig` (ChatClient bean, explicit connect/read timeout, `spring.ai.retry` max-attempts 2, `pull-model-strategy: never`). Env vars `OLLAMA_TEMPERATURE`, `OLLAMA_TIMEOUT_SECONDS` added to `.env.example`.
- **Tests (+44 surefire → 143 total)**: `AiServiceTest` (text/structured/repair/invalid/provider paths), `AiControllerTest` (200/400/401/502/503/504/422 envelope), `AiIntegrationTest` (full context via FakeLlmClient — **boots and serves with no Ollama**), `PromptServiceTest`, `AiDtoValidationTest`, `FakeLlmClientTest`, `LlmExceptionTest`, `OllamaLlmClientTest` (error mapping), `ArchitectureBoundaryTest` (AI↔domain + Spring-AI import isolation), and a **profile-gated live Ollama IT** (skipped in normal `verify`; run separately against real `llama3.2`, 3/3 PASS).

### Changed
- `pom.xml`: added the Spring AI BOM (`dependencyManagement`) + Ollama starter; JaCoCo excludes extended to `ai/config/**` and `ai/llm/ollama/**` (provider/infra). Boot **stays 3.4.1** (ADR-0009).

### Fixed
- Out-of-range enum values from a real model (surfaced by the live Ollama run: `priority: "FEATURE"`) now map to `LLM_INVALID_OUTPUT` (422) and feed the bounded repair path, instead of a generic provider error.

### Security
- AI endpoints authenticated by default (no `PUBLIC_ENDPOINTS` change); input bounded (≤4000, `@NotBlank`); no DB/tool access from the AI layer; prompts/responses never logged in full; local-first (`LLM_FALLBACK_ENABLED=false`, no cloud provider wired).

### Docs
- ADR-0009 (LLM provider abstraction & Ollama default), ADR-0010 (structured output strategy); updated `TECH_STACK`, `API`, `AGENT_ARCHITECTURE`, `TOOL_SYSTEM`, `DATA_PRIVACY`, `SECURITY`, `OBSERVABILITY`, `PERFORMANCE`, `TESTING`, `DEPLOYMENT`, `ROADMAP`, `README`, `backend/README`, `.env.example`.

---

## [0.0.3] — 2026-08-21 — Milestone 3: Core Domain (Task & Customer)

### Added
- **Task domain** (`task` package): `Task` entity, `TaskStatus` (TODO/IN_PROGRESS/COMPLETED/CANCELLED) and `TaskPriority` (LOW/MEDIUM/HIGH/CRITICAL) enums, `TaskRepository` (ownership-scoped nullable-filter query), `TaskService`, `TaskController`, `TaskMapper`, `TaskNotFoundException`, and DTOs (`TaskCreateRequest`, `TaskUpdateRequest`, `TaskResponse`, `TaskSummaryResponse`).
- **Customer domain** (`customer` package): `Customer` entity, `CustomerStatus` (ACTIVE/INACTIVE) enum, `CustomerRepository` (unique-email pre-check + own-scoped search query), `CustomerService`, `CustomerController`, `CustomerMapper`, `CustomerNotFoundException`, `CustomerEmailAlreadyExistsException` (409), and DTOs.
- **REST APIs** (all authenticated): `GET/POST /api/v1/tasks`, `GET/PUT/DELETE /api/v1/tasks/{id}`, and the same for `/api/v1/customers`. `POST`→201+`Location`, `PUT`→200 (full replacement), `DELETE`→204. Pagination (`page`, `size`≤100, whitelisted `sort`), filters (task: `status`/`priority`/`dueBefore`; customer: `status`/`search`), `PageResponse<T>` envelope.
- **Flyway migrations**: `V3__create_tasks.sql`, `V4__create_customers.sql` — `owner_id` FK `ON DELETE CASCADE`, CHECK constraints for enums, `UNIQUE(owner_id, email)` for customers, and ownership/query indexes (`(owner_id)`, `(owner_id, created_at)`, task `(owner_id, status|priority|due_date)`).
- **Ownership & authorization**: owner assigned server-side from the JWT principal (client `ownerId` ignored — mass-assignment prevented); by-id access load-then-authorize via `AuthorizationService.canAccess`; non-owner/missing → 404 (existence-masking); ADMIN own-list + admin-any-by-id.
- **Common**: `PageResponse<T>`, `SortWhitelist` (page/size clamp + sort-field whitelist), `InvalidRequestException` (400); `GlobalExceptionHandler` now maps `MethodArgumentTypeMismatchException` (bad enum query param) → 400.
- **Testcontainers PostgreSQL integration tests** (`*IT`, failsafe): `AbstractPostgresIntegrationTest` (`@DynamicPropertySource`, `disabledWithoutDocker`), `SchemaIT`, `TaskPersistenceIT`, `CustomerPersistenceIT` — verify the real migrations, CHECK/UNIQUE constraints, and FK cascade on `postgres:16-alpine`. `application-it.yml` profile added.
- **99 fast tests** (surefire, H2) + Testcontainers ITs. New: `SortWhitelistTest`, `Task*`/`Customer*` repository/mapper/service/API tests.

### Changed
- **JaCoCo enforcement gate activated** (M1/M2 was reporting-only): `verify` fails below 75% BUNDLE instruction coverage, with narrow excludes (bootstrap, `config`, DTO records, response envelopes). Current overall coverage ~88%; `TaskService`/`CustomerService` ≥ 80%.
- `pom.xml`: added Testcontainers (`junit-jupiter`, `postgresql`, test scope) and `maven-failsafe-plugin`; failsafe pins Docker `api.version=1.44` (docker-java in Testcontainers 1.20.x otherwise negotiates an API version Docker Engine 29 rejects with HTTP 400).

### Fixed
- Customer search query failed on real PostgreSQL (`function lower(bytea) does not exist`) when the `search` filter was null: a nullable `String` bind is untyped on PG. Fixed with `CAST(:search AS string)` in the JPQL. Caught by `CustomerPersistenceIT` (H2 had tolerated it) — the value the Testcontainers suite adds.

### Decisions
- ADR-0006 — Core domain ownership model. ADR-0007 — Domain persistence & primary-key strategy. ADR-0008 — Testcontainers PostgreSQL integration testing.

### Docs
- Reconciled: ownership FK column documented as `owner_id` (was `user_id` conceptually) in `DATABASE.md`; base path standardized to `/api/v1` in `API.md`; 404 (non-owner domain resource) vs 403 (RBAC) clarified in `TESTING.md`/`SECURITY.md`; ADR index corrected (0003–0005 were used in M2; M3 adds 0006–0008).
- Updated `DATABASE.md`, `API.md`, `SECURITY.md`, `TESTING.md`, `TECH_STACK.md`, `ROADMAP.md`, `DEPLOYMENT.md`, `DATA_PRIVACY.md`, `PERFORMANCE.md`, `OBSERVABILITY.md`, `AGENT_ARCHITECTURE.md`, `TOOL_SYSTEM.md`, `README.md`, `backend/README.md`, `.env.example`.

### Verified (2026-08-21)
- `./mvnw clean test` — 99 tests, all green (H2 fast suite). `./mvnw clean verify` — green, JaCoCo gate held (~88% overall).
- Testcontainers PostgreSQL ITs executed for real against `postgres:16-alpine` (Docker available): `SchemaIT`, `TaskPersistenceIT` (3), `CustomerPersistenceIT` (4) — migrations, CHECK/UNIQUE constraints, FK cascade, and the PostgreSQL-only search bug all verified.
- Ownership/security: USER→own 200, USER→other 404, ADMIN→any-by-id 200, ADMIN list own-only, client `ownerId` ignored, unauthenticated 401 — all asserted.

### Not included (later milestones)
- Spring AI/Ollama (M4), tool registry (M5), agent orchestration (M6), Redis memory (M7), guardrails (M8), audit (M9), metrics (M10), frontend (M13). No AI code added to the domain.

---

## [0.0.2] — 2026-08-21 — Milestone 2: Authentication & Authorization

### Added
- Persistence stack: Spring Data JPA + PostgreSQL driver + Flyway. Migrations `V1__create_users_and_roles.sql`, `V2__seed_roles.sql` (seeds `ROLE_USER`, `ROLE_ADMIN`; no users/passwords seeded).
- `user` package: `User`, `Role` entities (`users`/`roles`/`user_roles`, unique email, BCrypt hash) + repositories.
- `auth` package: `POST /api/v1/auth/register` (→ 201, always `ROLE_USER`) and `POST /api/v1/auth/login` (→ JWT); `RegisterRequest`/`LoginRequest`/`AuthResponse`/`UserResponse`; `EmailAlreadyExistsException` (409), `InvalidCredentialsException` (401).
- `security` package: `SecurityConfig` (stateless, deny-by-default, method security, restricted CORS, CSRF-off documented), `JwtService` (jjwt HS256), stateless `JwtAuthenticationFilter`, `AuthenticatedUser` principal, `SecurityUser` + `CustomUserDetailsService`, `BCryptPasswordEncoder`, JSON `RestAuthenticationEntryPoint` (401) / `RestAccessDeniedHandler` (403), `AuthorizationService` (reusable ownership foundation).
- Protected `GET /api/v1/me` (current principal) and ADMIN-only `GET /api/v1/admin/ping` (`@PreAuthorize`).
- OpenAPI Bearer (`bearerAuth`) security scheme — Swagger "Authorize" works.
- Unified error model: `common/exception/ApiException` base (M1 `ResourceNotFoundException` now extends it); handlers for `ApiException` and `AccessDeniedException`.
- 31 new tests (39 total): `AuthIntegrationTest` (20, full filter chain via MockMvc), `AuthHttpSocketTest` (3, real socket HTTP via `RANDOM_PORT`), `JwtServiceTest` (4), `AuthorizationServiceTest` (4). Test DB: H2 in PostgreSQL mode running the production migrations.
- Config: env-driven datasource + `security.jwt.*` + `security.cors.*`; `application-test.yml` H2 profile with a labeled test-only JWT secret.

### Changed
- `.env.example`: `JWT_EXPIRATION_MINUTES` → `JWT_EXPIRATION_SECONDS` (default 3600); added `CORS_ALLOWED_ORIGINS`; `DATABASE_*`/`JWT_SECRET` now required; profile `dev` → `local`.
- Docs reconciled: public auth route standardized to `/api/v1/auth/**`; JWT TTL unit standardized to seconds (both had been inconsistent in the M0 docs).
- `HealthControllerTest` now runs with security filters disabled (security exists as of M2).

### Decisions
- ADR-0003 — User and Role security model. ADR-0004 — JWT authentication strategy. ADR-0005 — Database migration & test-database strategy.

### Verified (2026-08-21)
- `./mvnw clean test` PASS (39/39) · `./mvnw verify` PASS · `./mvnw clean package` PASS.
- Real HTTP (socket, `RANDOM_PORT`): register 201 · login 200+JWT · `/me` 200 with token / 401 without · admin 403 for USER, 200 for ADMIN · public routes reachable · sensitive actuator endpoints not exposed.
- Secrets scan clean (only a labeled test-only JWT value committed).

### Not included (later milestones)
- Domain entities/CRUD (M3), Testcontainers-PostgreSQL (M3), Spring AI/Ollama (M4), tool registry (M5), agent orchestration (M6), Redis memory (M7), guardrails (M8), audit (M9), metrics (M10), login rate limiting & token rotation/revocation (later).

---

## [0.0.1] — 2026-08-21 — Milestone 1: Backend Foundation

### Added
- `backend/` Maven module — Spring Boot 3.4.1, Java 21, Maven Wrapper (3.9.9, script-only).
- Package structure under `com.prince.agentic`: `config`, `common/response`, `common/exception`, `health`.
- `AgenticApplication` entry point.
- Global error handling: `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping validation (400), malformed body (400), not-found (404), method-not-allowed (405), unknown route (404), and unexpected (500) to a standard `ApiError` envelope (`timestamp, status, error(code), message, path, traceId, fieldErrors`).
- `ResourceNotFoundException` (reusable domain exception).
- `GET /api/v1/health` technical endpoint (`HealthController` + `HealthResponse`) reporting real app name/version/active-profiles.
- Actuator `health` + `info` exposed; all other actuator endpoints closed by default.
- OpenAPI/Swagger via springdoc 2.7.0 (`OpenApiConfig`); docs mark domain/agent/auth APIs as PLANNED.
- Configuration profiles: `application.yml`, `application-local.yml`, `application-test.yml`; build-time version filtering (`@project.version@`).
- JaCoCo coverage **reporting** (0.8.12); enforcement gate deferred to M3.
- 8 tests: context load, health endpoint, error-handler mappings, config/version sanity.
- Multi-stage, non-root `backend/Dockerfile`.
- GitHub Actions CI (`.github/workflows/ci.yml`): JDK 21 + `./mvnw verify`.

### Decisions
- ADR-0001 — technology baseline (Spring Boot 3.4.1 / springdoc 2.7.0 / Maven wrapper / JaCoCo reporting).
- ADR-0002 — defer persistence (JPA/PostgreSQL/Flyway) to Milestone 3.

### Changed (docs)
- Reconciled the error-envelope contract in `API.md`/`ERROR_HANDLING.md` to include a machine `error` code and `path`; documented `traceId` as a per-response id (request-wide correlation is PLANNED for M10).
- Updated `ROADMAP.md`, `TECH_STACK.md`, `DATABASE.md`, `TESTING.md`, `OBSERVABILITY.md`, `DEPLOYMENT.md`, `README.md`.

### Verified (2026-08-21)
- `./mvnw clean test` PASS (8/8) · `./mvnw verify` PASS · `./mvnw clean package` PASS.
- Runtime: `/actuator/health`, `/actuator/info`, `/api/v1/health`, `/v3/api-docs`, `/swagger-ui/index.html` → HTTP 200; sensitive actuator endpoints → HTTP 404.

### Not included (later milestones)
- Authentication/authorization (M2), domain entities & persistence (M3), Spring AI/Ollama (M4), tool registry (M5), agent orchestration (M6), Redis memory (M7), guardrails (M8), audit (M9), metrics dashboards (M10), full Docker Compose stack (M12), frontend (M13).

---

## [0.0.0] — 2026-08-20 — Milestone 0: Starter Kit

### Added
- Engineering governance for the project:
  - Root `CLAUDE.md` operating manual, `README.md`, `.env.example`, `.gitignore`.
  - `.claude/` — `README.md`, 11 always-on rules (architecture, backend, ai-agent, security, database, api, testing, documentation, git, observability, performance), 12 command workflows, 10 reusable prompts.
  - `docs/` — PRD, project charter, roadmap (M0–M14), system & agent architecture, tool system, memory, guardrails, tech stack, coding standards, API contract, database design, error handling, security, threat model, audit logging, data privacy, observability, evaluation, testing, performance, non-functional requirements, deployment, documentation standards, skill routing map, definition of done, release checklist, ADR system.

### Notes
- **No application code exists yet.** This release is documentation and governance only. All feature/endpoint/metric references in the docs are PLANNED.
- Skill routing references only skills actually available in the environment.

---

_Starter kit initialized._
