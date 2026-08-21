# Roadmap
## Agentic AI Task Orchestrator

Milestone-based, dependency-ordered. Each milestone defines: **objective · prerequisites · outputs · validation · docs to update · definition of done**. A milestone is not "shipped" without evidence (see `DEFINITION_OF_DONE.md`, `RELEASE_CHECKLIST.md`).

**Legend:** ✅ done · 🟡 in progress · ⬜ planned.

| # | Milestone | Status |
|---|---|---|
| 0 | Starter Kit | ✅ |
| 1 | Backend Foundation | ✅ |
| 2 | Authentication & Authorization | ✅ |
| 3 | Core Domain | ✅ |
| 4 | Spring AI Integration | ⬜ |
| 5 | Tool Registry | ⬜ |
| 6 | Agent Orchestration | ⬜ |
| 7 | Memory | ⬜ |
| 8 | Guardrails | ⬜ |
| 9 | Auditing | ⬜ |
| 10 | Observability | ⬜ |
| 11 | Testing & Evaluation | ⬜ |
| 12 | Docker & Deployment | ⬜ |
| 13 | Frontend & Demo | ⬜ |
| 14 | Resume / Portfolio Finalization | ⬜ |

---

### Milestone 0 — Starter Kit ✅
- **Objective:** Establish engineering governance so future work is consistent and disciplined.
- **Prerequisites:** none.
- **Outputs:** `CLAUDE.md`, `.claude/{rules,commands,prompts,README}`, all `docs/*.md`, `README.md`, `.env.example`, `.gitignore`.
- **Validation:** every required file exists; no invented skills; no secrets; README does not claim unfinished work is done.
- **Docs:** this whole kit.
- **DoD:** structure check in the task's final section passes; kit is internally consistent.

### Milestone 1 — Backend Foundation ✅
- **Objective:** Runnable Spring Boot skeleton with layered structure, config, global error handling, health, and Swagger.
- **Prerequisites:** M0.
- **Delivered (IMPLEMENTED + VERIFIED):** `backend/` Maven module (Spring Boot 3.4.1, Java 21, Maven wrapper); `com.prince.agentic` package structure (`config`, `common/{response,exception}`, `health`); global `@RestControllerAdvice` with the standard `ApiError` envelope; three profiles (`application.yml`/`-local`/`-test`); Actuator `health`+`info` (sensitive endpoints not exposed); `GET /api/v1/health`; SpringDoc OpenAPI + Swagger UI; JaCoCo reporting; multi-stage non-root `Dockerfile`; GitHub Actions CI (`.github/workflows/ci.yml`); 8 tests.
- **Validation (VERIFIED 2026-08-21):** `./mvnw clean test` PASS (8/8), `./mvnw verify` PASS, `./mvnw clean package` PASS; app boots with zero external infra; `/actuator/health`, `/actuator/info`, `/api/v1/health`, `/v3/api-docs`, `/swagger-ui/index.html` all HTTP 200; sensitive actuator endpoints HTTP 404.
- **Decisions:** ADR-0001 (technology baseline), ADR-0002 (defer persistence to M3).
- **Docs updated:** `TECH_STACK.md`, `API.md`, `ERROR_HANDLING.md`, `DATABASE.md`, `TESTING.md`, `OBSERVABILITY.md`, `DEPLOYMENT.md`, `CHANGELOG.md`, `README.md`.
- **Deferred to later milestones:** persistence/JPA (M3), coverage-enforcement gate (M3), auth/security (M2), correlation-ID propagation (M10).

### Milestone 2 — Authentication & Authorization ✅
- **Objective:** JWT auth, BCrypt passwords, RBAC (USER/ADMIN), ownership enforcement pattern.
- **Prerequisites:** M1.
- **Delivered (IMPLEMENTED + VERIFIED):** persistence stack (JPA + PostgreSQL + Flyway; `users`/`roles`/`user_roles`, seeded roles); `auth` feature (`POST /api/v1/auth/register`, `/login`); `security` package (`SecurityConfig` deny-by-default, `JwtService` + stateless `JwtAuthenticationFilter`, `AuthenticatedUser` principal, `CustomUserDetailsService`, BCrypt, JSON 401/403 responders, `AuthorizationService` ownership foundation); protected `GET /api/v1/me`; ADMIN-only `GET /api/v1/admin/ping` (`@PreAuthorize`); Swagger Bearer scheme; 31 new tests (39 total).
- **Validation (VERIFIED 2026-08-21):** `./mvnw clean test` PASS (39), `./mvnw verify` PASS, `./mvnw clean package` PASS. Real socket-level HTTP verified via `RANDOM_PORT` (`AuthHttpSocketTest`); security matrix (register/login/JWT/RBAC/401/403/hashing/enumeration/actuator) green.
- **Decisions:** ADR-0003 (user/role model), ADR-0004 (JWT strategy), ADR-0005 (migration & test-DB strategy).
- **Docs updated:** `SECURITY.md`, `THREAT_MODEL.md`, `DATA_PRIVACY.md`, `API.md`, `DATABASE.md`, `TESTING.md`, `TECH_STACK.md`, `DEPLOYMENT.md`, `OBSERVABILITY.md`, `CHANGELOG.md`, `README.md`.
- **Deferred:** ownership on concrete resources (M3), Testcontainers-PostgreSQL (M3), login rate limiting & token rotation/revocation (later).

### Milestone 3 — Core Domain ✅
- **Objective:** Task and Customer domains with CRUD, owned by users.
- **Prerequisites:** M2.
- **Delivered (IMPLEMENTED + VERIFIED):** `task` and `customer` features (entities, enums, repositories, services, controllers, mappers, DTOs, not-found/conflict exceptions); Flyway `V3__create_tasks.sql` / `V4__create_customers.sql` (owner FK + cascade, CHECK constraints, `UNIQUE(owner_id,email)`, ownership/query indexes); server-assigned ownership with 404-masking and admin-any-by-id; pagination (size ≤100), whitelisted sorting, filters; `PageResponse<T>`, `SortWhitelist`, type-mismatch→400 handling; Testcontainers PostgreSQL ITs (`SchemaIT`, `TaskPersistenceIT`, `CustomerPersistenceIT`) + `maven-failsafe`; JaCoCo enforcement gate activated. 99 fast tests (H2) + real-PostgreSQL ITs.
- **Validation (VERIFIED 2026-08-21):** `./mvnw clean test` PASS (99); `./mvnw clean verify` PASS with the coverage gate held (~88%); Testcontainers ITs executed for real against `postgres:16-alpine` (migrations, CHECK/UNIQUE/FK-cascade, and a PostgreSQL-only search bug all verified); ownership/IDOR/admin/mass-assignment matrix green.
- **Decisions:** ADR-0006 (core domain ownership model), ADR-0007 (domain persistence & PK strategy), ADR-0008 (Testcontainers PostgreSQL integration testing).
- **Docs updated:** `DATABASE.md`, `API.md`, `SECURITY.md`, `TESTING.md`, `TECH_STACK.md`, `DEPLOYMENT.md`, `DATA_PRIVACY.md`, `PERFORMANCE.md`, `OBSERVABILITY.md`, `AGENT_ARCHITECTURE.md`, `TOOL_SYSTEM.md`, `CHANGELOG.md`, `README.md`, `backend/README.md`, `ADR/README.md`.
- **Deferred:** cross-user admin listing (dedicated admin API, later); soft delete; Spring AI/agent (M4+).

### Milestone 4 — Spring AI Integration ⬜
- **Objective:** LLM provider abstraction (`LlmClient`) over Ollama, with output parsed/validated into typed objects.
- **Prerequisites:** M3.
- **Outputs:** provider abstraction + Ollama impl; prompt templates in config/code; typed output parsing + repair/retry; `FakeLlmClient` for tests.
- **Validation:** unit tests with the fake; malformed-output (422/repair) path tested; no live LLM in CI.
- **Docs:** `AGENT_ARCHITECTURE.md` (model responsibilities), `TECH_STACK.md` (ADR for provider strategy), `DATA_PRIVACY.md`, `CHANGELOG.md`.
- **DoD:** abstraction is the only path to the model; tests deterministic.

### Milestone 5 — Tool Registry ⬜
- **Objective:** Explicit, typed, authorized, audited tool contract and registry with the first read-only + deterministic tools.
- **Prerequisites:** M4, M3.
- **Outputs:** tool interface + registry; `searchTasks`, `getTask`, `calculate`; per-tool input/output schemas, validation, authorization, side-effect classification, audit hook.
- **Validation:** tool unit tests incl. argument validation + authorization refusal; registry only exposes permitted tools.
- **Docs:** `TOOL_SYSTEM.md`, `AUDIT_LOGGING.md`, `CHANGELOG.md`.
- **DoD:** every tool meets the contract in `TOOL_SYSTEM.md`.

### Milestone 6 — Agent Orchestration ⬜
- **Objective:** The orchestration loop: decision → tool selection → authorization → validation → execution → observation → next decision → completion.
- **Prerequisites:** M5.
- **Outputs:** orchestrator; `POST /api/agent/chat`; execution state model; multi-step flows (U2/U3-class); side-effecting `createTask`/`updateTask` with authorization.
- **Validation:** agent tests over deterministic fakes for multi-step selection + argument accuracy; execution record persisted.
- **Docs:** `AGENT_ARCHITECTURE.md`, `API.md` (agent endpoints), `CHANGELOG.md`.
- **DoD:** LLM never bypasses authorization; execution is retrievable.

### Milestone 7 — Memory ⬜
- **Objective:** Redis-backed conversation/session/execution state and caching, cleanly separated from durable Postgres data.
- **Prerequisites:** M6.
- **Outputs:** Redis integration; conversation context + execution state with TTLs; Testcontainers Redis tests.
- **Validation:** state survives within a session and expires per TTL; no durable data placed in Redis.
- **Docs:** `MEMORY.md`, `PERFORMANCE.md` (caching), `CHANGELOG.md`.
- **DoD:** durable-vs-ephemeral split verified by tests.

### Milestone 8 — Guardrails ⬜
- **Objective:** Bounded, safe execution — max tool calls, timeout, retry limit, loop detection, confirmation for dangerous ops, output validation, rate limiting, cancellation.
- **Prerequisites:** M6.
- **Outputs:** guardrail enforcement in the orchestrator; confirmation flow; high-risk `deleteTask` gated; configurable bounds via env.
- **Validation:** tests for each bound tripping; confirmation required before dangerous ops; loop detection triggers.
- **Docs:** `GUARDRAILS.md`, `THREAT_MODEL.md`, `CHANGELOG.md`.
- **DoD:** no unbounded execution path exists.

### Milestone 9 — Auditing ⬜
- **Objective:** Durable, queryable audit trail of agent decisions, tool executions, side effects, and confirmations.
- **Prerequisites:** M6, M5.
- **Outputs:** audit persistence; `GET /api/agent/executions/{id}`; admin inspection endpoints; redaction of sensitive fields.
- **Validation:** every side-effecting tool call produces an audit record; admin can retrieve; no secrets/PII in audit.
- **Docs:** `AUDIT_LOGGING.md`, `DATA_PRIVACY.md`, `API.md`, `CHANGELOG.md`.
- **DoD:** who/what/when/which-tool/result present per event.

### Milestone 10 — Observability ⬜
- **Objective:** Metrics + structured logging + dashboards for backend and agent/tool execution.
- **Prerequisites:** M6.
- **Outputs:** Micrometer metrics (latency, failure rate, tool count, agent success rate, LLM duration); correlation/execution IDs in logs; Prometheus + Grafana in compose; dashboards.
- **Validation:** metrics scraped; dashboards render; IDs correlate a request end-to-end.
- **Docs:** `OBSERVABILITY.md`, `CHANGELOG.md`.
- **DoD:** an agent run is fully traceable via IDs and metrics.

### Milestone 11 — Testing & Evaluation ⬜
- **Objective:** Complete the test pyramid and a reproducible agent evaluation suite.
- **Prerequisites:** M6–M10.
- **Outputs:** unit/integration/E2E coverage to the gate; evaluation dataset + runner scoring tool-selection, argument accuracy, refusal, dangerous-op handling, completion, latency.
- **Validation:** `./mvnw verify` green at the gate; evaluation runs reproducibly and reports scores.
- **Docs:** `TESTING.md`, `EVALUATION.md`, `CHANGELOG.md`.
- **DoD:** evaluation is repeatable and CI-runnable (with pinned/mocked model where needed).

### Milestone 12 — Docker & Deployment ⬜
- **Objective:** One-command reproducible stack and green CI/CD.
- **Prerequisites:** M1–M11.
- **Outputs:** multi-stage Dockerfiles (non-root JRE), `docker-compose.yml` (postgres, redis, ollama, backend, frontend, prometheus, grafana) with healthchecks + ordering; CI build/test.
- **Validation:** clean-clone `docker-compose up --build` works; migrations apply; health green.
- **Docs:** `DEPLOYMENT.md`, `RELEASE_CHECKLIST.md`, `.env.example`, `CHANGELOG.md`.
- **DoD:** deploy checklist passes from a clean clone.

### Milestone 13 — Frontend & Demo ⬜
- **Objective:** Minimal React UI: auth, task/customer views, agent chat, execution history.
- **Prerequisites:** M2–M10.
- **Outputs:** React + Vite + TS app; typed API layer; protected routes; loading/empty/error states; demo script/screenshots.
- **Validation:** UI drives the real API end-to-end; demo reproducible.
- **Docs:** frontend rule (`.claude/rules/frontend.md` added when this milestone starts), `README.md` (screenshots), `CHANGELOG.md`.
- **DoD:** end-to-end demo works from a clean clone.

### Milestone 14 — Resume / Portfolio Finalization ⬜
- **Objective:** Turn verified capabilities into honest resume/portfolio claims.
- **Prerequisites:** M1–M13.
- **Outputs:** README polish, architecture diagram, measured metrics, ADR index, trade-off write-up; resume bullet points backed by IMPLEMENTED/TESTED/VERIFIED/MEASURED evidence.
- **Validation:** every claim traceable to code + evidence.
- **Docs:** `README.md`, `CHANGELOG.md`, ADRs.
- **DoD:** no claim exceeds what is implemented and verified.
