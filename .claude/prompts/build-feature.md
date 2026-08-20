# Prompt: Build a feature

Use when implementing a feature. Fill in the blanks, then follow the steps in order.

---

**Feature:** <name / what the user can do>
**Related docs:** <PRD story ID · API.md endpoint(s) · DATABASE.md tables · TOOL_SYSTEM.md tool(s) if agent-facing>
**Milestone:** <M?>

## Do this in order
1. **Read first.** Load `CLAUDE.md`, the relevant `docs/*.md`, and the matching `.claude/rules/*.md`. Read a sibling feature package to match patterns.
2. **Plan.** List the files you'll create/modify (controller, service, repository, DTOs, mapper, migration, tool, tests, frontend). State the approach. For significant work, present Understanding / Architecture Impact / Files Affected / Implementation Plan / Testing Plan / Risks and confirm before coding.
3. **Data layer.** Flyway migration + JPA entity per `docs/DATABASE.md`. Centralize constants.
4. **Service.** Business logic + ownership checks + `@Transactional`. LLM/tools via their abstractions only. No transaction across an LLM/tool call.
5. **Agent surface (if applicable).** Register the tool per `docs/TOOL_SYSTEM.md`: typed input/output, validation, authorization, side-effect class, timeout, retry, audit. Add confirmation for dangerous ops.
6. **API.** Request/response records with Bean Validation → controller → Swagger. Match `docs/API.md`; update it if the contract changes.
7. **Tests.** Unit (mock repos + LLM + tools) covering happy + error + auth paths; integration test (Testcontainers) for the endpoint/flow; agent tests for tool selection/argument validation if applicable. Cover 400/401/403/422 and guardrail limits.
8. **Observability.** Correlation/execution IDs on logs; metrics for the new path; audit records for side effects.
9. **Verify.** Run `./mvnw verify` (green + coverage gate). Update every doc touched + `CHANGELOG.md`.
10. **Summarize.** What changed, how to verify, trade-offs, follow-ups.

## Constraints
Obey all `.claude/rules/*.md`. Controller→Service→Repository. DTOs at boundaries. Validate input and model arguments. Authorize before effect. Validate LLM output. Bound agent execution. No secrets. Meet the full Definition of Done before claiming completion.

## Skills to use
- **Start:** `superpowers:brainstorming` → `superpowers:writing-plans`.
- **During:** `engineering:system-design`; area rules for the code you touch (backend/api/security/database/ai-agent/testing).
- **Finish:** `superpowers:requesting-code-review` + `engineering:code-review`, then `superpowers:verification-before-completion`.
- **Skip:** anything in `docs/SKILL_ROUTING_MAP.md` §5.
