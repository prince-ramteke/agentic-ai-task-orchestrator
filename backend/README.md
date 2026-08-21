# Backend — Agentic AI Task Orchestrator

Spring Boot backend module. Through **Milestone 4** it provides the backend foundation, the
authentication & authorization boundary (JWT, BCrypt, RBAC), the user-owned **Task**/**Customer**
domains (CRUD, ownership, pagination, PostgreSQL), and the **LLM foundation** — an `LlmClient`
abstraction over local **Ollama** via **Spring AI 1.0.9**, with `AiService`, validated structured
output, and authenticated `/api/v1/ai/*` demo endpoints. **No tool registry or agent yet** (M5–M6;
see [`../docs/ROADMAP.md`](../docs/ROADMAP.md)).

## Requirements

- **Java 21** (Temurin recommended). No global Maven needed — the Maven Wrapper (`./mvnw`) is included.
- **Running the app** requires a **PostgreSQL** and env vars (`DATABASE_*`, `JWT_SECRET`, …) — see [`../.env.example`](../.env.example). **Testing** needs neither (H2 + the real migrations).

## Build & test (no infrastructure)

```bash
./mvnw verify        # 143 fast tests on H2 running the production Flyway migrations, + coverage gate
```

**Live Ollama test (optional, opt-in).** The AI suite never needs a model, but you can verify a real
one end-to-end (needs `ollama serve` running and `ollama pull llama3.2`):
```bash
./mvnw -Dllm.live.ollama=true -Dit.test=OllamaLlmClientLiveIT verify
```
It is **skipped** in a normal `./mvnw verify`.

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
| `POST /api/v1/ai/generate` · `POST /api/v1/ai/classify` | Bearer | LLM text + typed classification (M4; needs Ollama, else `503 LLM_UNAVAILABLE`) |
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
├── ai/                           # M4 LLM layer: AiController, AiService, dto/, prompt/PromptService,
│                                 #   llm/ (LlmClient abstraction, exception model, ollama/OllamaLlmClient),
│                                 #   config/ (LlmProperties, AiConfig) — only ai.llm.ollama imports Spring AI
├── common/query/                 # SortWhitelist (page/size clamp + sort whitelist)
└── health/                       # HealthController, HealthResponse

resources/db/migration/           # V1 (users/roles), V2 (seed roles), V3 (tasks), V4 (customers)
resources/prompts/                # generate.st, classify.st (versioned prompt templates)
```

Configuration: `src/main/resources/application.yml` (+ `-local`, `-test` profiles). The reported version is filtered in from the build at package time.

## Conventions (enforced)

- Controller → Service → Repository layering; DTOs (records) at the API boundary.
- Constructor injection with `final` fields; no field `@Autowired`.
- All errors flow through `GlobalExceptionHandler` → the standard `ApiError` envelope. No stack traces or internals leak to clients.
- See [`../docs/CODING_STANDARDS.md`](../docs/CODING_STANDARDS.md), [`../docs/API.md`](../docs/API.md), [`../docs/ERROR_HANDLING.md`](../docs/ERROR_HANDLING.md), and [`../.claude/rules/`](../.claude/rules).

## Deliberately not present yet

Tool registry (M5), the agent runtime (M6+), Redis (M7), guardrails (M8), a cloud fallback provider (future). Dependencies are added by the milestone that needs them. (Spring AI/Ollama landed in M4 — the `LlmClient` layer only, no tools or agent.)

## Docker

A multi-stage, non-root `Dockerfile` is included. The full Compose stack arrives in Milestone 12.

```bash
docker build -t agentic-backend .   # requires a running Docker daemon
```
