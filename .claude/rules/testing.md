# Rule: Testing

Always-on testing constraints. See `docs/TESTING.md` and `docs/EVALUATION.md`.

## Always
- Write JUnit 5 + Mockito unit tests for new/changed logic, with real assertions.
- Mock the LLM provider, tool dependencies, and repositories in unit tests — deterministic, no network.
- Use deterministic fakes (`FakeLlmClient`, fake tools) for agent tests; cover argument validation, authorization refusal, repair/retry, and loop-bound paths.
- Add integration tests (`@SpringBootTest` + Testcontainers: Postgres, Redis) for new endpoints, queries, and stateful flows.
- Cover error/edge paths: invalid input (400), not owner (403), unauthenticated (401), malformed LLM output (422), guardrail trip (429/limit).
- Add/extend the agent **evaluation** dataset for any change to tool selection or prompts (see `docs/EVALUATION.md`).
- Run `./mvnw verify` (tests + coverage gate) green before pushing.

## Never
- Never call a live LLM or external network in a test.
- Never mark work done with failing or partial tests.
- Never write a test that only asserts "no exception thrown".
- Never drop coverage below the gate (targets in `docs/TESTING.md`).

## Naming
`XxxTest` (unit), `XxxIT` (integration). Methods: `method_condition_expected`. Arrange–Act–Assert.

## Work that belongs here
Unit tests, integration tests (Testcontainers), agent/AI behavior tests, evaluation datasets, edge/error-path coverage, and the coverage gate.

## Skills for this area
- **Auto-consult:** `engineering:testing-strategy`. Use `superpowers:test-driven-development` when test-first, `superpowers:systematic-debugging` when a test fails for an unknown reason.
- **Verify before done:** `superpowers:verification-before-completion` — never claim green without running `./mvnw verify`.
- **Ignore:** frontend/design and doc-format skills (unless testing the frontend, then pair with `rules/frontend` when it exists).
