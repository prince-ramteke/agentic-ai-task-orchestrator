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
| JaCoCo | 0.8.12 | Coverage reporting since M1; **enforcement gate active as of M3** (`verify` fails below 75% BUNDLE instruction coverage; excludes bootstrap/config/DTOs/response envelopes) |

Milestone 1 dependencies: `spring-boot-starter-web`, `-actuator`, `-validation`, `springdoc-openapi-starter-webmvc-ui`, `-test`.

**Added in Milestone 2 (auth):** `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `flyway-core` + `flyway-database-postgresql` (10.20.1), `org.postgresql:postgresql` (42.7.4, runtime), `io.jsonwebtoken:jjwt-api/impl/jackson` (0.12.6). Test-only: `spring-security-test`, `com.h2database:h2` (2.3.232). See ADR-0003/0004/0005.

**Added in Milestone 3 (core domain):** test-only `org.testcontainers:junit-jupiter` + `org.testcontainers:postgresql` (versions managed by the Spring Boot 3.4.1 BOM — Testcontainers 1.20.x) and the `maven-failsafe-plugin` (runs `*IT` integration tests in `verify`). No new runtime dependencies. Testcontainers ITs skip cleanly without Docker (`disabledWithoutDocker`) and run for real in Docker-capable CI (ADR-0008). The failsafe plugin pins the Docker Remote API version to `1.44` (docker-java bundled in Testcontainers 1.20.x otherwise negotiates a version Docker Engine 29 rejects with HTTP 400).

**Added in Milestone 4 (Spring AI / LLM foundation — IMPLEMENTED + VERIFIED):** `org.springframework.ai:spring-ai-starter-model-ollama`, versioned by the imported **`spring-ai-bom` 1.0.9** (`<spring-ai.version>` property). The 1.0.x line is the one compatible with Spring Boot 3.4.x; the BOM manages only `spring-ai-*` artifacts, so **Spring Boot stays 3.4.1** — no upgrade (ADR-0009, satisfying `CLAUDE.md` §32). Only the Ollama starter is added: **no** tool-calling/function modules, vector store, or Redis (those arrive with M5/M7). The model is reached solely through the project's own `LlmClient` abstraction (`OllamaLlmClient` is the only class importing `org.springframework.ai.*`, enforced by a test). Structured output uses Spring AI's converter, re-validated with Bean Validation (ADR-0010). Any cloud provider is still **not** present.

**Added in Milestone 7 (Redis conversation memory — IMPLEMENTED + VERIFIED):** `org.springframework.boot:spring-boot-starter-data-redis` (Lettuce; version managed by the Boot 3.4.1 parent) for ephemeral, bounded, TTL'd agent conversation memory — never durable domain data (PostgreSQL remains the source of truth; `MEMORY.md`, ADR-0017). Test scope adds `org.testcontainers:testcontainers` for a real `redis:7-alpine` container in the `*IT` suite (skips cleanly without Docker). No Kafka, vector store, or cloud provider added. Spring Boot stays **3.4.1**.

**Added in Milestone 5 (tool registry & execution framework — IMPLEMENTED + VERIFIED):** **no new dependencies.** The `com.prince.agentic.tool` framework (`Tool<I,O>`, `ToolDescriptor`, `ToolRegistry`, `ToolExecutor`, `ToolResult`, six tools, safe `ExpressionEvaluator`) is built entirely on existing Spring, Jackson (argument binding), Bean Validation, and Micrometer. It deliberately does **not** depend on Spring AI (enforced by a boundary test); the Spring AI tool-calling adapter is M6. Spring Boot stays 3.4.1. Redis, Kafka, and vector stores remain **not** present (ADR-0011/0012).

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

## Milestone 6 — Agent Orchestration (no new dependencies)

M6 added the `com.prince.agentic.agent` layer (orchestrator, planner, decision contract, tool-catalog adapter, bounded observations, cooperative budgets, loop detection, `POST /api/v1/agent/execute`) using **only existing dependencies**: Spring Web/Security/Validation, Jackson, Micrometer, the M4 `LlmClient`, and JUnit 5/Mockito/Testcontainers for tests. No new runtime or test dependency, no Flyway migration, no datastore. Spring AI stays confined to `OllamaLlmClient`; the agent uses the `LlmClient` abstraction. See ADR-0013…0016.

## Milestone 8 — Guardrails & Agent Safety Enforcement (no new dependencies)

M8 added the `com.prince.agentic.guardrail` layer (policy engine, risk/argument policies, SHA-256
`FingerprintService`, Redis-backed single-use `RedisConfirmationService`, per-user `RedisRateLimiter`,
guardrail/confirmation exceptions) and the orchestrator integration (confirmation halt, confirm/cancel
endpoints) using **only existing dependencies**: Spring Web/Security/Validation, Jackson, Micrometer,
the M7 `StringRedisTemplate` (Lettuce), and `java.security.MessageDigest` for fingerprinting; JUnit
5/Mockito/Testcontainers for tests. **No new runtime or test dependency, no Bucket4j, no Flyway
migration, no new datastore.** Confirmation and rate state reuse Redis under separate namespaces
(`guard:confirmation:{id}`, `guard:rate:{userId}:{epochMinute}`). Spring Boot stays **3.4.1**. See
ADR-0021…0025.

## Milestone 9 — Durable Agent Audit (no new dependencies)

M9 added the `com.prince.agentic.audit` package (Flyway `V5` three-table schema, JPA entities +
repositories, a repository-free `AgentExecutionListener` seam, best-effort `AuditService`/`AuditWriter`,
and the read API) using **only existing dependencies**: Spring Web/Data JPA/Validation, Jackson,
Micrometer, Flyway, the M8 `FingerprintService` (for `arguments_hash`), and JUnit
5/Mockito/Testcontainers for tests. **No new runtime or test dependency, no Hibernate Envers, no
Kafka/Elasticsearch, no new datastore** — audit is durable PostgreSQL alongside the existing domain
tables. Spring Boot stays **3.4.1**. See ADR-0026…0029.

## Milestone 10 — Observability & Retention Enforcement (one new dependency)

M10 adds exactly one runtime dependency — `io.micrometer:micrometer-registry-prometheus` (version
from the Boot 3.4.1 BOM) — to expose `/actuator/prometheus`. Everything else reuses existing
building blocks: Spring Boot Actuator (health/liveness/readiness probes, info), Micrometer
(counters/timers already used since M4), SLF4J MDC + Logback (pattern update only), Spring
`@Scheduled` (no external scheduler), Spring JDBC (`JdbcTemplate` for the retention repo — no new
JPA entity), and `java.util.concurrent.locks.ReentrantLock` for single-node purge overlap
protection. **No Prometheus/Grafana/OTel/Kafka/Elasticsearch is deployed** — the app is simply
made compatible with them (they arrive with the M12 docker-compose profile). Spring Boot stays
**3.4.1**. See ADR-0030 (observability taxonomy + correlation) and ADR-0031 (retention
enforcement).
