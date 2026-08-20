# Backend — Agentic AI Task Orchestrator

Spring Boot backend module. Through **Milestone 2** it provides the backend foundation plus the
authentication & authorization boundary (JWT, BCrypt, RBAC, user/role persistence). No domain,
Spring AI, or agent functionality yet (see [`../docs/ROADMAP.md`](../docs/ROADMAP.md)).

## Requirements

- **Java 21** (Temurin recommended). No global Maven needed — the Maven Wrapper (`./mvnw`) is included.
- **Running the app** requires a **PostgreSQL** and env vars (`DATABASE_*`, `JWT_SECRET`, …) — see [`../.env.example`](../.env.example). **Testing** needs neither (H2 + the real migrations).

## Build & test (no infrastructure)

```bash
./mvnw verify        # 39 tests against H2 running the production Flyway migrations
```

## Run (needs PostgreSQL + env)

```bash
# export DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD / JWT_SECRET first
./mvnw spring-boot:run
```

Default port: `8080`. Default profile: `local`. Flyway applies the schema at startup.

## Verify it's up

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /api/v1/auth/register` | public | Register (always `ROLE_USER`) |
| `POST /api/v1/auth/login` | public | Authenticate → JWT |
| `GET /api/v1/me` | Bearer | Current authenticated principal |
| `GET /api/v1/admin/ping` | ADMIN | RBAC demonstration |
| `GET /api/v1/health` · `/actuator/health` · `/actuator/info` | public | Liveness / metadata |
| `GET /swagger-ui.html` | public | Swagger UI (click **Authorize** to send a JWT) |

Deny-by-default: any other route needs a valid Bearer token. Only `health`/`info` Actuator
endpoints are exposed (others: 401 anonymous / 404 authenticated).

## Test

```bash
./mvnw clean test     # unit + slice tests
./mvnw verify         # full lifecycle + JaCoCo coverage report (target/site/jacoco/index.html)
```

## Structure

```
com.prince.agentic
├── AgenticApplication            # entry point
├── config/OpenApiConfig          # OpenAPI metadata + Bearer scheme
├── common/response/              # ApiError, FieldValidationError (error envelope)
├── common/exception/             # ApiException base, GlobalExceptionHandler
├── security/                     # SecurityConfig, JwtService, JwtAuthenticationFilter,
│                                 #   AuthenticatedUser, AuthorizationService, 401/403 responders
├── auth/                         # register/login controller, service, DTOs, exceptions
├── user/                         # User/Role entities + repositories
├── account/                      # MeController (/api/v1/me)
├── admin/                        # AdminController (/api/v1/admin/ping)
└── health/                       # HealthController, HealthResponse

resources/db/migration/           # V1 (users/roles/user_roles), V2 (seed roles)
```

Configuration: `src/main/resources/application.yml` (+ `-local`, `-test` profiles). The reported version is filtered in from the build at package time.

## Conventions (enforced)

- Controller → Service → Repository layering; DTOs (records) at the API boundary.
- Constructor injection with `final` fields; no field `@Autowired`.
- All errors flow through `GlobalExceptionHandler` → the standard `ApiError` envelope. No stack traces or internals leak to clients.
- See [`../docs/CODING_STANDARDS.md`](../docs/CODING_STANDARDS.md), [`../docs/API.md`](../docs/API.md), [`../docs/ERROR_HANDLING.md`](../docs/ERROR_HANDLING.md), and [`../.claude/rules/`](../.claude/rules).

## Deliberately not present yet

Domain entities/CRUD & Testcontainers-PostgreSQL (M3), Spring AI/Ollama (M4), tool registry (M5), the agent runtime (M6+), Redis (M7). Dependencies are added by the milestone that needs them.

## Docker

A multi-stage, non-root `Dockerfile` is included. The full Compose stack arrives in Milestone 12.

```bash
docker build -t agentic-backend .   # requires a running Docker daemon
```
