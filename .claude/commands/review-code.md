# Command: /review-code

Structured self-review of a change before merge.

**Usage:** `/review-code` (reviews the current diff) or `/review-code <path/PR>`

## Steps
1. **Invoke** `engineering:code-review` and `superpowers:requesting-code-review`.
2. **Scope.** Get the diff (`git diff`) or the named target. Read the changed files in full, not just the hunks.
3. **Check against rules.** For each area touched, verify the relevant `.claude/rules/*.md` Always/Never lists. Flag every violation.
4. **Correctness.** Logic errors, edge cases, null/optional handling, transaction boundaries, error paths routed through the handler.
5. **Security & agent safety.** Auth/ownership before effect, input/argument validation, no secrets/PII in logs, injection resistance, bounded execution (see `.claude/rules/security.md`, `ai-agent.md`).
6. **Tests.** Do tests exist for happy + error + auth paths? Real assertions? Any coverage drop?
7. **Docs.** Are `docs/*.md`, Swagger, and `CHANGELOG.md` updated for behavior/contract changes?
8. **Report** findings ranked by severity, each with file:line and a concrete fix. Distinguish blocking from nice-to-have.

Enforces: rules-checked · security/agent-checked · tests-and-docs verified before merge.
