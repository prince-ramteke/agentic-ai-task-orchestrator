# Changelog
## Agentic AI Task Orchestrator

All notable changes to this project are recorded here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/). Do not fabricate history — add an entry only for a change that actually happened.

Categories: **Added · Changed · Fixed · Removed · Security · Docs**.

---

## [Unreleased]

_Work in progress toward Milestone 2 — Authentication & Authorization._

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
