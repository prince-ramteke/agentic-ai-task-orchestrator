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
| 4 | Spring AI Integration | ✅ |
| 5 | Tool Registry | ✅ |
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

### Milestone 4 — Spring AI Integration ✅
- **Objective:** LLM provider abstraction (`LlmClient`) over Ollama, with output parsed/validated into typed objects.
- **Prerequisites:** M3.
- **Delivered (IMPLEMENTED + VERIFIED):** `com.prince.agentic.ai` feature — `LlmClient` abstraction (`generate`/`generateStructured`/`info`), `OllamaLlmClient` via **Spring AI 1.0.9** (`spring-ai-starter-model-ollama`, `spring-ai-bom`, Boot 3.4.1 unchanged — ADR-0009), `FakeLlmClient` (test), `AiService` (validation + bounded one-repair), `PromptService` + templates, AI exception model (`LLM_UNAVAILABLE`/`LLM_TIMEOUT`/`LLM_PROVIDER_ERROR`/`LLM_INVALID_OUTPUT` via the existing `ApiException`/`GlobalExceptionHandler`), timeout + conservative retry, structured output via Spring AI converter re-validated with Bean Validation (ADR-0010), authenticated `POST /api/v1/ai/generate` + `/classify`, `llm.request.*` metrics, `ArchitectureBoundaryTest` enforcing AI↔domain isolation. **No** agent/tools/Redis/guardrails/fallback.
- **Validation (VERIFIED 2026-08-21):** `./mvnw clean test` PASS (143 surefire); `./mvnw clean verify` PASS with the coverage gate held (~88%); Testcontainers Postgres ITs ran for real; the gated live Ollama IT is **skipped** in normal `verify` and was **run separately against real `llama3.2` (3/3 PASS)** — live model VERIFIED. The live run also surfaced and drove a fix: out-of-range model enums now map to `LLM_INVALID_OUTPUT` and feed the repair path.
- **Decisions:** ADR-0009 (LLM provider abstraction & Ollama default), ADR-0010 (structured output strategy).
- **Docs updated:** `TECH_STACK.md`, `AGENT_ARCHITECTURE.md`, `TOOL_SYSTEM.md`, `DATA_PRIVACY.md`, `SECURITY.md`, `OBSERVABILITY.md`, `PERFORMANCE.md`, `API.md`, `TESTING.md`, `DEPLOYMENT.md`, `.env.example`, `CHANGELOG.md`, `README.md`, `backend/README.md`, `ADR/README.md`.
- **Deferred:** tool registry/tool-calling (M5), agent orchestration (M6), Redis/memory (M7), guardrails/confirmation (M8), agent audit (M9), Prometheus/Grafana dashboards (M10), cloud fallback provider (documented future).

### Milestone 5 — Tool Registry ✅
- **Objective:** Explicit, typed, authorized tool contract and registry with the first read-only + deterministic tools.
- **Prerequisites:** M4, M3.
- **Delivered (IMPLEMENTED + VERIFIED):** `com.prince.agentic.tool` framework — `Tool<I,O>`, `ToolDescriptor`, `ToolRiskLevel` (`READ_ONLY/DETERMINISTIC/SIDE_EFFECTING/HIGH_RISK`), `ToolExecutionContext` (identity from the authenticated principal, never arguments), `ToolResult<O>` envelope, fail-fast immutable `ToolRegistry` (O(1)), `ToolExecutor` (ordered gates: resolve→authenticate→authorize→bind→validate→execute→wrap; unknown args rejected), tool exception model (`TOOL_*` via `ApiException`). Six tools: `task.get`, `task.search`, `task.create`, `customer.get`, `customer.search`, `math.calculate` (safe evaluator, no `eval`). Two-layer authz (role any-of in the executor + resource ownership delegated to the M3 services — ADR-0012). ADMIN read-only `GET /api/v1/tools`. `tool.execution.*` Micrometer metrics. Boundary test enforces independence from `ai.*`/persistence. **No** agent/LLM tool-calling/Redis/guardrails/durable-audit.
- **Validation (VERIFIED 2026-08-21):** `./mvnw clean test` PASS; `./mvnw clean verify` PASS with the coverage gate held. Tool contract test (`AbstractToolContractTest`) applied to every tool; executor gate matrix (not-found/unauthorized/forbidden/invalid-input/domain-error/unexpected); security IT over real services on H2 (ownership 404-masking, admin-any-by-id, spoofed-owner rejected, anonymous unauthorized, destructive tools not registered); calculator arithmetic + malformed/dangerous rejection; ADMIN/USER/anonymous on `GET /api/v1/tools`. No Ollama needed.
- **Decisions:** ADR-0011 (tool abstraction & registry), ADR-0012 (tool authorization & execution-context boundary).
- **Docs updated:** `TOOL_SYSTEM.md`, `AGENT_ARCHITECTURE.md`, `SECURITY.md`, `GUARDRAILS.md`, `EVALUATION.md`, `OBSERVABILITY.md`, `API.md`, `TESTING.md`, `PERFORMANCE.md`, `TECH_STACK.md`, `CHANGELOG.md`, `README.md`, `backend/README.md`, `ADR/README.md`.
- **Deferred:** agent orchestration (M6), Redis/memory (M7), guardrails/confirmation/hard-timeout (M8), durable tool/agent audit tables (M9), update/delete tools, Spring AI tool-calling adapter (M6).

### Milestone 6 — Agent Orchestration ✅ IMPLEMENTED
- **Objective:** The orchestration loop: decision → tool selection → authorization → validation → execution → observation → next decision → completion.
- **Prerequisites:** M5.
- **Outputs:** `AgentOrchestrator` (bounded loop); `POST /api/v1/agent/execute`; in-memory single-request execution state; multi-step flows over the M5 registry (`task.get`/`task.search`/`task.create` etc.); cooperative bounds (iteration/tool-call budgets, one deadline, cancellation seam, loop detection); orchestration metrics. **Hard** guardrail enforcement, confirmation, and rate limiting remain **M8**; durable execution records + a retrieval endpoint remain **M9**.
- **Validation:** unit tests (decision validation, budgets, deadline/cancellation, loop detection, observation bounds, catalog, planner); orchestrator tests over deterministic fakes (multi-step selection, argument accuracy, refusal/limit/timeout/loop paths); full Testcontainers-Postgres integration incl. own-data/cross-user-404/admin-any-by-id/identity-spoof/unregistered-tool security cases. `verify` needs no live Ollama.
- **Docs:** `AGENT_ARCHITECTURE.md`, `GUARDRAILS.md`, `API.md` (agent endpoint), `TOOL_SYSTEM.md`, `SECURITY.md`, `OBSERVABILITY.md`, `CHANGELOG.md`; ADR-0013…0016.
- **DoD:** LLM never bypasses authorization; every effect flows through the M5 `ToolExecutor`; no unbounded execution path. *(Durable, retrievable execution records: M9.)*

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
