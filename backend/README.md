# Backend — Agentic AI Task Orchestrator

Spring Boot backend module. Through **Milestone 3** it provides the backend foundation, the
authentication & authorization boundary (JWT, BCrypt, RBAC, user/role persistence), and the first
user-owned business domains — **Task** and **Customer** (CRUD, ownership, pagination/filtering,
PostgreSQL). No Spring AI or agent functionality yet (see [`../docs/ROADMAP.md`](../docs/ROADMAP.md)).

## Requirements

- **Java 21** (Temurin recommended). No global Maven needed — the Maven Wrapper (`./mvnw`) is included.
- **Running the app** requires a **PostgreSQL** and env vars (`DATABASE_*`, `JWT_SECRET`, …) — see [`../.env.example`](../.env.example). **Testing** needs neither (H2 + the real migrations).

## Build & test (no infrastructure)

```bash
./mvnw verify        # 99 fast tests on H2 running the production Flyway migrations, + coverage gate
```

Testcontainers PostgreSQL integration tests (`*IT`) run in `verify` **when a Docker engine is
available** (they verify the real migrations/constraints on `postgres:16-alpine`); without Docker
they skip cleanly and the build stays green.

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
| `GET/POST /api/v1/tasks` · `GET/PUT/DELETE /api/v1/tasks/{id}` | Bearer | User-owned tasks (CRUD, pagination, filters) |
| `GET/POST /api/v1/customers` · `GET/PUT/DELETE /api/v1/customers/{id}` | Bearer | User-owned customers (CRUD, search) |
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
├── task/                         # Task entity/enums, repository, service, controller, mapper, DTOs
├── customer/                     # Customer entity/enum, repository, service, controller, mapper, DTOs
├── common/query/                 # SortWhitelist (page/size clamp + sort whitelist)
└── health/                       # HealthController, HealthResponse

resources/db/migration/           # V1 (users/roles), V2 (seed roles), V3 (tasks), V4 (customers)
```

Configuration: `src/main/resources/application.yml` (+ `-local`, `-test` profiles). The reported version is filtered in from the build at package time.

## Conventions (enforced)

- Controller → Service → Repository layering; DTOs (records) at the API boundary.
- Constructor injection with `final` fields; no field `@Autowired`.
- All errors flow through `GlobalExceptionHandler` → the standard `ApiError` envelope. No stack traces or internals leak to clients.
- See [`../docs/CODING_STANDARDS.md`](../docs/CODING_STANDARDS.md), [`../docs/API.md`](../docs/API.md), [`../docs/ERROR_HANDLING.md`](../docs/ERROR_HANDLING.md), and [`../.claude/rules/`](../.claude/rules).

## Deliberately not present yet

Spring AI/Ollama (M4), tool registry (M5), the agent runtime (M6+), Redis (M7). Dependencies are added by the milestone that needs them. (Domain CRUD and Testcontainers-PostgreSQL landed in M3.)

## Docker

A multi-stage, non-root `Dockerfile` is included. The full Compose stack arrives in Milestone 12.

```bash
docker build -t agentic-backend .   # requires a running Docker daemon
```
