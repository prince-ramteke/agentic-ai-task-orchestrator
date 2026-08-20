# ADR-0002: Defer persistence (JPA/PostgreSQL/Flyway) to Milestone 3

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M1 — Backend Foundation

## Context
Milestone 1 asks for a clean foundation for future persistence, but explicitly not the business schema. The question is whether to add Spring Data JPA, the PostgreSQL driver, and Flyway now (before any entity exists) or to defer them.

Adding `spring-boot-starter-data-jpa` now forces a datasource: without one the context fails to start, and with one the application would require a running PostgreSQL just to boot — breaking the milestone goal that the app "starts successfully in local development" with zero external infrastructure. The result would also be empty scaffolding (no entities, no repositories), which the milestone brief forbids ("do not force persistence implementation before the domain model exists").

## Decision
Defer all persistence to **Milestone 3 (Core Domain)**, when the first entities exist. In Milestone 1:
- Do **not** add JPA, the PostgreSQL driver, or Flyway.
- Document the planned datasource wiring (env-var driven) as a commented, inactive block in `application.yml`.
- Keep `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` in `.env.example` as PLANNED (they are unused until M3).

## Alternatives considered
- **Add JPA now with a configured datasource** — requires a running Postgres to start the app; breaks zero-infra local startup and adds no value without entities.
- **Add JPA now but exclude `DataSourceAutoConfiguration`** — startable, but leaves a half-wired persistence layer and dead configuration; a classic over-engineering smell.
- **Add only the PostgreSQL driver now** — a dependency with no datasource and no consumer; violates "add only what this milestone needs".

## Consequences
- Positive: the app boots with no external infrastructure; the dependency tree stays intentional; no empty/fake persistence code.
- Positive: M3 introduces JPA + driver + Flyway + entities together, as one coherent, testable change (with Testcontainers).
- Negative: PostgreSQL connectivity is not demonstrated in M1 — acceptable, and clearly marked PLANNED in the docs.

## Links
- `docs/DATABASE.md`, `docs/ROADMAP.md` (M3), `backend/src/main/resources/application.yml`, `.env.example`
