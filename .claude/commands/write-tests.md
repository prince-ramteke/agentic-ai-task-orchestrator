# Command: /write-tests

Add or fill test coverage to the project's standard.

**Usage:** `/write-tests <component / feature / file>`

## Steps
1. **Load** `.claude/rules/testing.md` and `docs/TESTING.md`. Invoke `engineering:testing-strategy`.
2. **Map what needs coverage.** Identify the happy path plus error/edge paths: 400 (invalid input), 401/403 (auth), 404, 422 (malformed model output), and guardrail limits for agent code.
3. **Unit tests.** JUnit 5 + Mockito. Mock the LLM provider, tools, and repositories. Real assertions, Arrange–Act–Assert, `method_condition_expected` naming.
4. **Agent tests** (if applicable). Use deterministic fakes to assert tool selection, argument validation, authorization refusal, repair/retry, and loop-bound behavior.
5. **Integration tests.** `@SpringBootTest` + Testcontainers (Postgres, Redis) for endpoints/queries/stateful flows. `XxxIT` naming.
6. **Evaluation** (if tool selection or prompts changed). Extend the dataset in `docs/EVALUATION.md`.
7. **Verify.** Run `./mvnw verify`; confirm the coverage gate holds. Report coverage and evidence.

Never write a test that only asserts "no exception". Never call a live LLM or network.
