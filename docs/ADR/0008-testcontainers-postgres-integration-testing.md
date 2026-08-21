# ADR-0008: Testcontainers PostgreSQL Integration Testing

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince

## Context

Through M2, tests ran on H2 in PostgreSQL-compatibility mode executing the production Flyway
migrations — reproducible without Docker (ADR-0005). But H2 is not PostgreSQL: it does not catch
PostgreSQL-specific behaviour (type inference, constraint semantics, dialect quirks). M3 introduces
real business persistence, so we need to verify the actual migrations and JPA mappings against real
PostgreSQL — while keeping `./mvnw verify` runnable on machines without Docker.

## Decision

- Add **Testcontainers PostgreSQL integration tests** (`*IT`, run by `maven-failsafe-plugin` in the
  `integration-test`/`verify` phases) on a shared base class (`AbstractPostgresIntegrationTest`)
  that starts a `postgres:16-alpine` container and wires Spring's datasource with
  `@DynamicPropertySource`. It uses the **singleton-container pattern** — one container started once
  and shared by every IT class — because a per-class `@Container` is stopped after the first IT
  class while Spring's cached test context still points at that container's port, producing
  `HikariPool ... connection is not available` timeouts on the next IT class. Docker is detected
  once; when absent the container is not started and every IT is **skipped via a JUnit assumption**
  (equivalent to `@Testcontainers(disabledWithoutDocker = true)`), so `verify` stays green everywhere.
- **Retain the H2 fast suite** (`*Test`, surefire) as the coverage-bearing suite. Both suites run the
  identical Flyway migrations.
- ITs are **required to run in Docker-capable CI**; PostgreSQL integration is only claimed *verified*
  when the ITs actually executed against a container.
- **Docker API version pin:** the failsafe plugin sets the system property `api.version=1.44`. The
  docker-java client bundled with Testcontainers 1.20.x negotiates an API version newer than Docker
  Engine 29 accepts (server max 1.54), so its `/info` probe fails with HTTP 400 ("no valid Docker
  environment"). 1.44 is within every modern engine's range (server min 1.40), so the pin is portable
  across local Docker Desktop and Linux CI.

## Alternatives considered

- **Replace H2 with Testcontainers entirely:** rejected — would make `verify` require Docker, breaking
  local/no-Docker runs and any CI without Docker.
- **`@ServiceConnection`:** rejected — needs the extra `spring-boot-testcontainers` dependency and
  interacts awkwardly with the base `${DATABASE_URL}` placeholder; `@DynamicPropertySource` overrides
  the datasource cleanly with one fewer dependency.

## Consequences

- Real-PostgreSQL confidence where Docker exists (this is how the `lower(bytea)` nullable-parameter
  bug in the customer search query was caught — H2 passed, PostgreSQL failed), and a green build
  everywhere else, with honest verification reporting.
- A small, documented environment coupling: the `api.version=1.44` pin. If Testcontainers/docker-java
  is upgraded to a version that negotiates correctly against Docker 29+, the pin can be removed.

## Links

- Spec: `docs/superpowers/specs/2026-08-21-m3-core-domain-design.md`
- `docs/TESTING.md` §4/§8, ADR-0005 (H2 fast suite), ADR-0007 (the PostgreSQL-only bug this caught).
