# Backend — Agentic AI Task Orchestrator

Spring Boot backend module. **Milestone 1 (Backend Foundation)** — a clean, runnable skeleton that later milestones build on. No domain, security, persistence, or agent functionality yet (see [`../docs/ROADMAP.md`](../docs/ROADMAP.md)).

## Requirements

- **Java 21** (Temurin recommended). No global Maven needed — the Maven Wrapper (`./mvnw`) is included.
- No database or other infrastructure is required for Milestone 1.

## Run

```bash
./mvnw spring-boot:run
```

Or build a jar and run it:

```bash
./mvnw clean package
java -jar target/agentic-ai-task-orchestrator-0.0.1-SNAPSHOT.jar
```

Default port: `8080`. Default profile: `local`.

## Verify it's up

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Operational health (Actuator) |
| `GET /actuator/info` | App metadata (name/version) |
| `GET /api/v1/health` | App liveness + name/version/active-profiles |
| `GET /v3/api-docs` | OpenAPI document |
| `GET /swagger-ui.html` | Swagger UI |

Only `health` and `info` Actuator endpoints are exposed; all others return 404 by design.

## Test

```bash
./mvnw clean test     # unit + slice tests
./mvnw verify         # full lifecycle + JaCoCo coverage report (target/site/jacoco/index.html)
```

## Structure

```
com.prince.agentic
├── AgenticApplication            # entry point
├── config/OpenApiConfig          # OpenAPI metadata
├── common/response/              # ApiError, FieldValidationError (error envelope)
├── common/exception/             # ResourceNotFoundException, GlobalExceptionHandler
└── health/                       # HealthController, HealthResponse
```

Configuration: `src/main/resources/application.yml` (+ `-local`, `-test` profiles). The reported version is filtered in from the build at package time.

## Conventions (enforced)

- Controller → Service → Repository layering; DTOs (records) at the API boundary.
- Constructor injection with `final` fields; no field `@Autowired`.
- All errors flow through `GlobalExceptionHandler` → the standard `ApiError` envelope. No stack traces or internals leak to clients.
- See [`../docs/CODING_STANDARDS.md`](../docs/CODING_STANDARDS.md), [`../docs/API.md`](../docs/API.md), [`../docs/ERROR_HANDLING.md`](../docs/ERROR_HANDLING.md), and [`../.claude/rules/`](../.claude/rules).

## Deliberately not present yet

Persistence/JPA & PostgreSQL (M3 — see `../docs/ADR/0002-defer-persistence-to-m3.md`), Spring Security/JWT (M2), Spring AI/Ollama (M4), Redis (M7), the agent runtime (M6+). Dependencies are added by the milestone that needs them.

## Docker

A multi-stage, non-root `Dockerfile` is included. The full Compose stack arrives in Milestone 12.

```bash
docker build -t agentic-backend .   # requires a running Docker daemon
```
