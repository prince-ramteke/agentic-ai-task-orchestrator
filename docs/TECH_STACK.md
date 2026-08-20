# Technology Stack
## Agentic AI Task Orchestrator

Every technology here has an architectural reason. Nothing is added to look impressive. Swapping a core choice requires an ADR (`docs/ADR/`).

## Core stack (committed)

| Technology | Role | Why this, here |
|---|---|---|
| **Java 21** | Language | Modern LTS: records (DTOs), pattern matching, virtual threads available if needed. |
| **Spring Boot 3.4.1** | Application framework | Mature DI, web, data, and security with minimal ceremony; the target-role standard. Version pinned in ADR-0001 (aligned with springdoc 2.7.0). |
| **Spring Web (REST)** | API layer | Straightforward, well-understood REST; pairs with SpringDoc. |
| **Spring Data JPA + Hibernate** | Persistence | Productive mapping to Postgres; repositories fit the layered design. |
| **Spring Security 6** | AuthN/AuthZ | JWT + RBAC + method security (`@PreAuthorize`) for authorizing AI-initiated actions. |
| **Spring AI** | LLM integration | First-class tool/function calling and provider abstraction; keeps the model behind an interface. |
| **Ollama** | Local model runtime | Runs models locally so no user data leaves the machine in dev (`DATA_PRIVACY.md`). |
| **PostgreSQL** | Durable datastore | Relational integrity for users, tasks, customers, executions, audit. |
| **Redis** | Ephemeral state + cache | Conversation/session/execution state and caching with TTLs (`MEMORY.md`). |
| **Flyway** | Schema migrations | Versioned, forward-only, reviewable DDL. |
| **Bean Validation** | Input validation | Declarative validation of DTOs and tool arguments. |
| **SpringDoc OpenAPI** | API docs | Swagger UI + machine contract kept next to the code. |
| **Micrometer** | Metrics facade | Vendor-neutral metrics → Prometheus. |
| **Prometheus + Grafana** | Metrics store + dashboards | Standard, container-friendly observability. |
| **Maven** | Build | Ubiquitous in the Spring ecosystem; simple CI. |
| **JUnit 5 + Mockito** | Unit testing | Standard; deterministic mocking of LLM/tools/repos. |
| **Testcontainers** | Integration testing | Real Postgres/Redis in tests without external infra. |
| **Docker + Docker Compose** | Packaging + local stack | One-command reproducible environment. |
| **GitHub Actions** | CI | Build + test on every PR; branch protection. |

## Pinned versions as of Milestone 1 (IMPLEMENTED)

| Component | Version | Notes |
|---|---|---|
| Java | 21 (Temurin) | LTS |
| Spring Boot | 3.4.1 | ADR-0001 |
| springdoc-openapi | 2.7.0 | Officially aligned with Spring Boot 3.4.x |
| Maven Wrapper | 3.9.9 (script-only) | Reproducible build, no global Maven needed |
| JaCoCo | 0.8.12 | Coverage **reporting** only in M1; enforcement gate deferred to M3 |

Milestone 1 dependencies: `spring-boot-starter-web`, `-actuator`, `-validation`, `springdoc-openapi-starter-webmvc-ui`, `-test`.

**Added in Milestone 2 (auth):** `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `flyway-core` + `flyway-database-postgresql` (10.20.1), `org.postgresql:postgresql` (42.7.4, runtime), `io.jsonwebtoken:jjwt-api/impl/jackson` (0.12.6). Test-only: `spring-security-test`, `com.h2database:h2` (2.3.232). Redis and Spring AI are still **not** present (added by the milestones that need them). See ADR-0003/0004/0005.

## Frontend (Milestone 13)

React + Vite + TypeScript · Axios (typed API layer) · React Router. A component/styling system (Tailwind **or** MUI — pick one, stay consistent) chosen when the milestone starts.

## Deferred until justified (needs an ADR before adoption)

| Technology | Would add | Adopt only when |
|---|---|---|
| Spring WebFlux | Reactive/streaming | Streaming agent responses become a real requirement. |
| Kafka | Event-driven integration | Async, decoupled workflows are genuinely needed. |
| OpenTelemetry | Distributed tracing | The system spans multiple services worth tracing. |
| pgvector | Vector search | A retrieval/knowledge-search tool is on the critical path. |
| External model providers (e.g. OpenAI) | Cloud fallback/quality | Privacy review passes and fallback is explicitly enabled. |
| Cloud deployment | Hosting | The demo needs to be publicly hosted. |

## Principles

- Prefer boring, well-supported technology over novelty.
- One datastore role per store: Postgres durable, Redis ephemeral — never blurred.
- The model stays behind an abstraction; vendors are swappable, features aren't coupled to a vendor SDK.
- Add a dependency only with a stated reason; record significant choices as ADRs.
