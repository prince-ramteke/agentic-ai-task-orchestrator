# Backend — Agentic AI Task Orchestrator

Spring Boot backend module. Through **Milestone 6** it provides the backend foundation, the
authentication & authorization boundary (JWT, BCrypt, RBAC), the user-owned **Task**/**Customer**
domains (CRUD, ownership, pagination, PostgreSQL), the **LLM foundation** (`LlmClient` over local
**Ollama** via **Spring AI 1.0.9**, `AiService`, `/api/v1/ai/*`), the **tool framework** — a
typed, validated, authorized execution boundary (`Tool`/`ToolDescriptor`/`ToolRegistry`/`ToolExecutor`)
with six least-privilege tools and an ADMIN `/api/v1/tools` catalog — and, as of **M6**, the **agent**:
`AgentOrchestrator`, a bounded, backend-controlled loop (`com.prince.agentic.agent`) that drives the
M5 `ToolExecutor` from a validated, LLM-produced `AgentDecision`, exposed at `POST /api/v1/agent/execute`.
The LLM is an untrusted planner; identity is always the authenticated principal; the run is bounded by
cooperative iteration/tool-call budgets, one deadline, a cancellation seam, and loop detection.
**Hard** guardrails/confirmation/rate-limiting are **M8**; Redis memory **M7**; durable audit **M9**
(see [`../docs/ROADMAP.md`](../docs/ROADMAP.md)).

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
| `GET /api/v1/tools` | ADMIN | Registered tool metadata (M5; read-only) |
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
├── tool/                         # M5 tool framework: Tool, ToolDescriptor, ToolRiskLevel, ToolExecutionContext,
│                                 #   ToolResult, ToolRegistry, ToolExecutor, exception/, math/ (safe calculator),
│                                 #   task/ + customer/ (domain tools), api/ (ADMIN GET /api/v1/tools).
│                                 #   Imports no ai.*/persistence (enforced by ToolArchitectureBoundaryTest)
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

The agent runtime and Spring AI tool-calling adapter (M6), Redis (M7), guardrails/confirmation (M8), durable agent/tool audit (M9), a cloud fallback provider (future). Dependencies are added by the milestone that needs them. (The M5 tool framework landed with **no new dependencies** — the deterministic tools only; the agent that drives them is M6.)

## Docker

A multi-stage, non-root `Dockerfile` is included. The full Compose stack arrives in Milestone 12.

```bash
docker build -t agentic-backend .   # requires a running Docker daemon
```
