# Testing Strategy
## Agentic AI Task Orchestrator

> Tests are added with each milestone. Deterministic, no live network/LLM.

> **Milestone 1 status (VERIFIED 2026-08-21):** 8 tests — context load (`AgenticApplicationTests`), health endpoint (`HealthControllerTest`), error-handler mapping via standalone MockMvc (`GlobalExceptionHandlerTest`), and config/version-filtering sanity (`ApplicationConfigTest`).

> **Milestone 2 status (VERIFIED 2026-08-21):** **39 tests total, all green** under `./mvnw verify`. Security suite: `AuthIntegrationTest` (20, `@SpringBootTest` + MockMvc through the real filter chain — register/login/JWT/RBAC/error-envelopes/password-hash/DB-constraint), `AuthHttpSocketTest` (3, `RANDOM_PORT` + `TestRestTemplate` — **real socket-level HTTP** over embedded Tomcat), `JwtServiceTest` (4 — roundtrip, expired, malformed, forged signature), `AuthorizationServiceTest` (4 — owner/admin/other/null ownership). Tests run against **H2 in PostgreSQL-compat mode executing the production Flyway migrations** — reproducible without Docker (ADR-0005). Testcontainers-PostgreSQL integration is deferred to M3.

## 1. Test pyramid

```
        E2E (few)            request -> agent -> tools -> result
      Integration           @SpringBootTest + Testcontainers (Postgres, Redis, API)
   Unit (many)              services, tools, validators, security, mappers
   Evaluation (parallel)    agent behavior vs. dataset (EVALUATION.md)
```

## 2. Unit tests

- JUnit 5 + Mockito. Mock the LLM provider, tool dependencies, and repositories.
- Cover: business rules, validators, mappers, security/authorization logic, and each tool (happy path, argument-validation failure, authorization refusal, audit produced for side-effecting, confirmation-required for high-risk).
- Real assertions; Arrange–Act–Assert; `method_condition_expected`. Never "asserts no exception".

## 3. Agent / AI tests (deterministic)

Use `FakeLlmClient` and fake tools to make the loop deterministic. Cover:
- Tool selection over a scripted decision sequence.
- Argument validation and rejection of bad model arguments.
- Authorization refusal (agent cannot exceed user permissions).
- Multi-step workflow (e.g. search → calculate → create).
- Repair/retry on malformed output.
- Guardrail bounds tripping (max calls, timeout, retry cap, loop detection).
- Confirmation flow for dangerous ops.
- Prompt-injection resistance (`THREAT_MODEL.md`).

## 4. Integration tests

- `@SpringBootTest` + Testcontainers: real Postgres and Redis; real HTTP via MockMvc/WebTestClient.
- Cover: endpoints (happy + 400 + 401/403 + 422), CRUD with ownership, Flyway migrations applying, Redis TTL/state behavior, and durable execution-record persistence.
- Naming: `XxxIT`.

## 5. End-to-end tests

The full path: authenticated request → orchestrator → tools → domain → Postgres → response + execution record. At least the flagship multi-step objective (U3) runs end-to-end against the deterministic fake model.

## 6. Evaluation suite

Runs in parallel to the pyramid; scores agent behavior against the dataset (`EVALUATION.md`). CI-runnable with a pinned/mocked model.

## 7. Error/edge coverage (required)

Invalid input (400), unauthenticated (401), not owner (403), not found (404), malformed model output (422), guardrail budget (429). Security tests for injection and authorization refusal.

## 8. Coverage gate (targets)

- Service/domain logic: **≥ 80%**.
- Overall: **≥ 75%**.
- Tools and guardrails: every branch of validation/authorization/bounds covered.
- **Status:** JaCoCo **reporting** is enabled now (M1); the **enforcement gate** (a failing `jacoco:check` in `./mvnw verify`) is activated in **M3**, once real domain logic exists so the thresholds are meaningful rather than trivially failing on a skeleton (ADR-0001). Do not drop below the gate once enforced.

## 9. Rules

- **Never** call a live LLM or external network in a test.
- **Never** mark work done with failing/partial tests.
- **Never** write a test that only asserts no-exception.
- Run `./mvnw verify` green before pushing (`DEFINITION_OF_DONE.md`).
