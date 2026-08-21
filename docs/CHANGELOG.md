# Changelog
## Agentic AI Task Orchestrator

All notable changes to this project are recorded here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/). Do not fabricate history — add an entry only for a change that actually happened.

Categories: **Added · Changed · Fixed · Removed · Security · Docs**.

---

## [Unreleased]

_Next: Milestone 6 — Agent Orchestration (not started)._

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
