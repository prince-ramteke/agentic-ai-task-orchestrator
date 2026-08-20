# CLAUDE.md

Operating manual for Claude Code (and any AI assistant) working in this repository.
**Read this file first. Follow it exactly. When in doubt, prefer the rules in `.claude/rules/` and the specs in `docs/`.**

> **Project status: MILESTONE 0 — STARTER KIT.** No application code exists yet. This repository currently contains only engineering governance (docs, rules, commands, prompts). Do not claim any feature, endpoint, test, or metric exists until it is actually implemented and verified. See `docs/ROADMAP.md`.

---

## 1. What this project is

**Agentic AI Task Orchestrator** — a secure, production-style backend where a user gives a natural-language objective and an LLM-driven agent completes it by selecting and executing **explicitly registered, permission-controlled backend tools**, observing their results, and continuing until the task is done.

Example objective:

> "Find all my overdue tasks, calculate the total estimated hours, and create a high-priority follow-up task."

Agent execution (conceptual): search overdue tasks → inspect results → calculate total hours → create follow-up task → return an execution summary.

**The LLM decides *what* tool to call. The backend decides *whether* it is allowed and *how* it actually runs.** The LLM never touches the database or infrastructure directly.

Source-of-truth documents:

| Need | File |
|---|---|
| Why it exists, scope, users, success criteria | `docs/PRD.md`, `docs/PROJECT_CHARTER.md` |
| Milestones & sequencing | `docs/ROADMAP.md` |
| Components & request flows | `docs/SYSTEM_ARCHITECTURE.md` |
| Agent lifecycle & responsibilities | `docs/AGENT_ARCHITECTURE.md` |
| Tool contract & risk classes | `docs/TOOL_SYSTEM.md` |
| Guardrails & bounded execution | `docs/GUARDRAILS.md` |
| Auth, RBAC, tool authorization | `docs/SECURITY.md`, `docs/THREAT_MODEL.md` |
| Data model | `docs/DATABASE.md` |
| API contract | `docs/API.md` |
| Redis / memory model | `docs/MEMORY.md` |
| Observability & audit | `docs/OBSERVABILITY.md`, `docs/AUDIT_LOGGING.md` |
| Test & evaluation strategy | `docs/TESTING.md`, `docs/EVALUATION.md` |
| Which skill for which task | `docs/SKILL_ROUTING_MAP.md` |

If you are about to make a design decision, check whether one of those documents already answers it. If a doc is wrong, flag it and fix it in the same change — never silently contradict it.

---

## 2. Required technology direction (do not swap without an ADR)

**Backend:** Java 21 · Spring Boot 3.x · Spring AI · Spring Web (REST) · Spring Data JPA + Hibernate · Spring Security 6 (JWT + RBAC) · Bean Validation · SpringDoc OpenAPI · Maven.

**AI:** Spring AI with a provider abstraction; **Ollama (local default)** for development. Function/tool calling routed through the project's own tool registry — never direct model-to-infrastructure access.

**Data & memory:** PostgreSQL (durable application data) · Redis (short-lived conversation/session/execution state, caching).

**Infra & ops:** Docker + Docker Compose · GitHub Actions CI · Micrometer → Prometheus → Grafana.

**Testing:** JUnit 5 · Mockito · Testcontainers.

**Frontend (later milestone):** React + Vite + TypeScript.

**Deferred until justified by an ADR:** Spring WebFlux, Kafka, OpenTelemetry, pgvector, external model providers, cloud deployment. Do not add technology to look impressive — every dependency needs an architectural reason (see `docs/TECH_STACK.md`).

---

## 3. Architecture rules (hard constraints)

1. **Layered flow:** Controller → Service → Repository. Controllers hold no business logic and never call repositories directly.
2. **DTOs at every boundary.** Never expose or accept JPA entities in the API. Use request/response records.
3. **The agent layer is separate from business logic.** Domain services work with or without the agent; the agent orchestrates over them via tools.
4. **LLM/embeddings access only through the provider abstraction.** No feature calls Ollama/OpenAI SDKs directly.
5. **Every tool is an explicit, registered capability** with typed input, validation, authorization, deterministic execution, structured output, and audit (see `docs/TOOL_SYSTEM.md`).
6. **Bounded agent execution.** Max tool calls, timeouts, retry limits, loop detection, cancellation (see `docs/GUARDRAILS.md`).
7. **Security by default.** New endpoints are authenticated unless explicitly whitelisted (register/login/health/swagger). Admin routes use `@PreAuthorize`.
8. **No secrets in code or git.** Config via environment variables / `.env` (git-ignored). Keep `.env.example` current.

Avoid: unnecessary microservices, premature abstraction, god services, business logic in controllers or in prompt strings, unrestricted LLM access, magic values, duplicated logic. Prefer the simplest solution that satisfies the requirement.

---

## 4. Agent safety rules (non-negotiable — full detail in `.claude/rules/ai-agent.md`)

- **The LLM is not the source of truth.** The application remains authoritative over every decision that has an effect.
- **Never trust model-generated arguments.** Validate every tool argument before execution.
- **Authorization before execution.** A side-effecting tool must verify the authenticated user's permission on the target resource *before* it runs.
- **Least privilege.** The agent is offered only the tools allowed in the current context.
- **Dangerous operations require confirmation** (delete data, send external messages, modify critical records, irreversible actions).
- **Bounded execution.** Enforce max tool calls, timeout, retry limit, and loop detection.
- **Full auditability.** Every significant agent step (decision, tool selection, execution, side effect, failure) is traceable by execution ID and correlation ID.
- **Treat all model input as untrusted.** User text, tool outputs re-fed to the model, and external data can all carry injection. Delimit them; never let them override system instructions.
- **Prompt changes are behavior changes.** Treat a prompt edit like a code change: review it and re-run evaluations.

---

## 5. Coding conventions (full detail in `docs/CODING_STANDARDS.md`)

- Java 21, constructor injection with `final` fields — never field `@Autowired`.
- Package-by-feature under `com.prince.agentic.<feature>` (e.g. `auth`, `task`, `customer`, `agent`, `tool`, `memory`, `llm`, `audit`, `common`).
- Records for DTOs; domain exceptions mapped centrally by a `@RestControllerAdvice`.
- `@Transactional` on writes, `readOnly = true` on reads. **Never hold a DB transaction open across a slow LLM/tool call.**
- SLF4J structured logging with correlation/execution IDs. No `System.out.println`. Never log secrets, tokens, or full prompts/documents.

---

## 6. Testing requirements (full detail in `docs/TESTING.md`)

- New/changed logic gets JUnit 5 + Mockito unit tests with real assertions.
- Mock `LlmClient`/tool dependencies; use deterministic fakes for agent tests. **No live LLM or network in tests.**
- Integration tests via `@SpringBootTest` + Testcontainers (Postgres, Redis) for endpoints and stateful flows.
- Agent behavior is evaluated with a representative dataset (`docs/EVALUATION.md`): tool-selection accuracy, argument accuracy, refusal on unauthorized actions, dangerous-operation handling.
- `./mvnw verify` must be green before a task is "done".

---

## 7. Definition of Done (apply to every change — full checklist in `docs/DEFINITION_OF_DONE.md`)

A change is done only when ALL that apply are true: requirements understood → relevant docs & rules read → relevant skill(s) used → implemented → input validated & errors routed through the global handler → security reviewed → logging/metrics/audit considered → tests added and `./mvnw verify` green → docs (incl. `docs/API.md`/Swagger) updated → no secrets committed → git diff reviewed → trade-offs and risks stated.

**Never claim work is complete without running the build/tests. Evidence before assertions.** Distinguish clearly between PLANNED, IMPLEMENTED, TESTED, VERIFIED, MEASURED.

---

## 8. Git & workflow rules (full detail in `.claude/rules/git.md`)

- Small, reviewable, single-purpose changes. One feature/bugfix per change set.
- Meaningful commit messages (imperative subject; explain *why*).
- Branch off `main`; do not commit or push unless the user asks. Never commit secrets.
- Significant architectural choices get an ADR (`docs/ADR/`).

---

## 9. Skill-routing behavior (full map in `docs/SKILL_ROUTING_MAP.md`)

Apply specialized skills **automatically but sparingly** — the fewest that fit the task. Repo `docs/` and `.claude/rules/` always outrank any external skill; when they conflict, follow the repo.

- **Any new feature:** `superpowers:brainstorming` → `superpowers:writing-plans`, then area skills.
- **Backend / API / DB / agent design:** `engineering:system-design` + the matching `.claude/rules/*.md`.
- **Bugs:** `engineering:debug` / `superpowers:systematic-debugging`.
- **Testing:** `engineering:testing-strategy` (+ `superpowers:test-driven-development` when test-first).
- **Deploy:** `engineering:deploy-checklist`.
- **Docs:** `engineering:documentation`.
- **Before "done" / merge:** `superpowers:requesting-code-review` + `engineering:code-review` + `superpowers:verification-before-completion`.
- **Architecture decisions (ADR-worthy):** `engineering:architecture`.
- **Do NOT auto-apply:** resume/job-search skills, brand/marketing design, ops rituals, memory/plugin tooling, doc-format skills (docx/pdf/pptx/xlsx) — manual, on explicit request only (see `docs/SKILL_ROUTING_MAP.md` §5).

---

## 10. Required workflow for future implementation tasks

Whenever the user later says **"Build X"**, before editing files determine:

1. What type of task is this? (feature / bug / refactor / infra / docs)
2. Which `.claude/rules/*.md` apply?
3. Which available skills are relevant?
4. Which existing architecture/components are affected? (read first — do not duplicate)
5. Which `docs/*.md` must be read first?
6. What files already exist?
7. Is an ADR needed?
8. What tests are required?
9. What security implications exist?
10. What observability/audit implications exist?

**For any architecturally significant change, present this before implementing:**

```
Understanding
Architecture Impact
Files Affected
Implementation Plan
Testing Plan
Risks
```

Then implement in small logical steps. Do not blindly start editing files. Do not silently change architectural decisions. Preserve backward compatibility where practical, and explain important trade-offs.

---

## 11. Commands available (see `.claude/commands/`)

`/new-feature` · `/add-endpoint` · `/fix-bug` · `/review-code` · `/write-tests` · `/design-api` · `/design-database` · `/security-review` · `/refactor` · `/deploy` · `/documentation-update` · `/ship-milestone`. Reusable prompts live in `.claude/prompts/`. See `.claude/README.md` for how the pieces fit together.
