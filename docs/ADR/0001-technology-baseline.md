# ADR-0001: Technology baseline (Spring Boot, build, coverage)

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M1 — Backend Foundation

## Context
Milestone 1 stands up the Spring Boot foundation. The framework version must be compatible with Java 21 today and with the technologies planned for later milestones — Spring AI (M4), Spring Security 6 (M2) — while keeping the OpenAPI tooling on an officially-aligned version. `docs/TECH_STACK.md` commits to the stack; this ADR pins the concrete versions and the build toolchain.

## Decision
- **Spring Boot 3.4.1** as the platform (parent POM).
- **springdoc-openapi 2.7.0**, the version officially built against Spring Boot 3.4.x.
- **Java 21** (Temurin), **Maven** via the **Maven Wrapper** (`mvnw`, script-only distribution, Maven 3.9.9 pinned) so the build is reproducible without a global Maven install.
- **JaCoCo 0.8.12** for coverage **reporting** only in M1 (no enforcement gate yet — see ADR note below and `docs/TESTING.md`).
- Milestone-1 dependencies limited to: `spring-boot-starter-web`, `-actuator`, `-validation`, `springdoc-openapi-starter-webmvc-ui`, `-test`.

## Alternatives considered
- **Spring Boot 3.5.x** — newer, also Java-21 compatible, but its officially-aligned springdoc is 2.8.x; 3.4.1 + springdoc 2.7.0 is the proven, documented pairing and both were already resolvable/cached, de-risking the build.
- **Spring Boot 4.x** — too new; ecosystem (Spring AI, springdoc) alignment not yet settled for this project's needs.
- **Gradle** — viable, but `docs/TECH_STACK.md` commits to Maven for ecosystem familiarity and simple CI.
- **Global Maven install** — rejected in favour of the wrapper for reproducibility.

## Consequences
- Positive: proven-compatible, reproducible build; OpenAPI aligned; forward-compatible with Spring AI 1.0.x and Spring Security 6.4.x.
- Negative: 3.4.x is one minor line behind the latest; a future ADR may bump to 3.5.x/4.x when the AI/security dependencies land and their alignment is confirmed.
- The coverage **gate** (enforcement thresholds) is intentionally deferred; revisit when real domain logic exists (M3) so the gate is meaningful rather than trivially failing on a skeleton.

## Links
- `docs/TECH_STACK.md`, `docs/TESTING.md`, `backend/pom.xml`
