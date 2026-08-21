# Changelog
## Agentic AI Task Orchestrator

All notable changes to this project are recorded here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/). Do not fabricate history — add an entry only for a change that actually happened.

Categories: **Added · Changed · Fixed · Removed · Security · Docs**.

---

## [Unreleased]

_Next: Milestone 4 — Spring AI + Ollama Integration (not started)._

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
