# Skill Routing Map
## Agentic AI Task Orchestrator

> The single source of truth for **which skill to use for which task**. Claude consults this (via `CLAUDE.md`) to auto-select the right guidance. Skill names here are **actually available** in this environment — no invented skills. Where no skill fits, use the standard Claude Code engineering workflow.
>
> **Golden rule:** use the *fewest* skills that fit the task. Repo `docs/` and `.claude/rules/` always outrank any external skill.

---

## 1. How routing works

1. Detect the **area/type** of the task (backend, agent/AI, api, database, security, testing, deploy, docs, refactor, bug, architecture).
2. Read the matching `.claude/rules/<area>.md` and relevant `docs/*.md`.
3. Consult **only** the skills mapped to that area below.
4. Run review/verification skills before "done".
5. Everything in §5 is ignored unless the user explicitly asks.

Priority: **P1** = consult first in this area · **P2** = task-specific · **P3** = review/verify or manual.

---

## 2. Core routing (available skills)

| Skill | What it does | When to use | Priority |
|---|---|---|---|
| **superpowers:brainstorming** | Explores intent/requirements before building | Before ANY new feature or non-trivial change | P1 |
| **superpowers:writing-plans** | Turns a spec into a phased plan | Multi-step work before touching code | P1 |
| **engineering:system-design** | Designs APIs, data models, service/tool boundaries | Backend, API, DB, agent/tool design | P1 (backend/api/db/agent) |
| **engineering:architecture** | Creates/evaluates an ADR (trade-offs) | Choosing tech or a hard-to-reverse design | P2 |
| **engineering:debug** | Structured reproduce→isolate→fix | A defect/stack trace/unexpected behavior | P1 (bugs) |
| **superpowers:systematic-debugging** | Disciplined debugging before proposing fixes | Any failing test / unexpected behavior | P1 (bugs) |
| **engineering:testing-strategy** | Designs test plans & coverage | Deciding what/how to test | P1 (testing) |
| **superpowers:test-driven-development** | Test-before-implementation | Implementing test-first | P2 (testing) |
| **engineering:code-review** | Reviews for security/perf/correctness | Before merging any change; security & agent reviews | P3 |
| **superpowers:requesting-code-review** | Structures a self-review pre-merge | Completing a feature/milestone | P3 |
| **superpowers:verification-before-completion** | Forces evidence before "done" | Right before claiming complete | P3 |
| **superpowers:finishing-a-development-branch** | Integrating a completed branch | Merge/finish a branch | P2 (git) |
| **engineering:tech-debt** | Identifies & prioritizes refactors | Planning cleanup/refactor | P2 (refactor) |
| **engineering:deploy-checklist** | Pre-deploy verification | Before shipping / verifying the stack | P1 (deploy) |
| **engineering:documentation** | Technical docs/README/runbooks | Authoring/updating docs | P1 (docs) |

> There is **no dedicated "security" or "agent-safety" build skill.** For those, the authoritative guidance is this repo: `.claude/rules/security.md`, `.claude/rules/ai-agent.md`, `docs/SECURITY.md`, `docs/THREAT_MODEL.md`, `docs/TOOL_SYSTEM.md`, `docs/GUARDRAILS.md` — verified with `engineering:code-review`.

---

## 3. Area → skills lookup

| Working on… | Auto-consult (P1/P2) | Verify (P3) |
|---|---|---|
| **Backend** (controllers/services/repos/DTOs) | rules/backend, rules/api, rules/security, rules/database, rules/testing · engineering:system-design | engineering:code-review, verification-before-completion |
| **Agent / AI** (orchestrator/tools/LLM/prompts) | rules/ai-agent, rules/security, rules/testing · engineering:system-design · docs/AGENT_ARCHITECTURE, TOOL_SYSTEM, GUARDRAILS, EVALUATION | engineering:code-review, verification-before-completion, evaluation suite |
| **API** (endpoints/contracts/Swagger) | rules/api, rules/security, rules/backend · engineering:system-design | engineering:code-review |
| **Database** (schema/entities/migrations/indexes) | rules/database, rules/backend · engineering:system-design (architecture for a storage ADR) | engineering:code-review |
| **Security** (auth/RBAC/ownership/secrets/agent safety) | rules/security, rules/ai-agent, rules/api · (rules-first) | engineering:code-review |
| **Testing** (unit/integration/agent/eval) | rules/testing · engineering:testing-strategy, TDD | verification-before-completion |
| **Observability / Performance** | rules/observability, rules/performance · engineering:system-design (tech-debt for perf refactors) | engineering:code-review |
| **Deployment** (Docker/Compose/CI) | rules/deployment (added at M12), rules/security · engineering:deploy-checklist | verification-before-completion |
| **Docs** (README/PRD/arch/roadmap/ADR) | rules/documentation · engineering:documentation | (self-review) |
| **New feature (any)** | superpowers:brainstorming → writing-plans → area skills | requesting-code-review, verification |
| **Bug** | engineering:debug / systematic-debugging → area skills | verification-before-completion |
| **Refactor** | engineering:tech-debt → area skills | engineering:code-review |
| **Architecture decision** | engineering:architecture (ADR) + engineering:system-design | engineering:code-review |

---

## 4. Frontend (from Milestone 13)

Available and relevant when the React app is built: **frontend-design:frontend-design** (visual direction), **ui-styling** (Tailwind/shadcn components), **ui-ux-pro-max** (layout/color/a11y patterns), **impeccable** (polishing an existing screen). Pair with `.claude/rules/api.md` (match the contract) and a `rules/frontend.md` added at M13. Ignore these until the frontend milestone.

---

## 5. Do-NOT-use-by-default (ignored unless explicitly requested)

| Group | Examples | Why excluded here |
|---|---|---|
| Personal job-search | resume-* / the-recruiter / the-hiring-manager / the-rewriter / the-diagnoser | Improve *your* resume/interview prep — this project *builds* an agent backend, a different thing. Use them for your own job hunt, not the codebase. |
| Brand/marketing design | brand, banner-design, design, design-system, slides, brandkit | Visual-identity work, not product engineering. |
| Ops rituals | engineering:incident-response, engineering:standup, morning, schedule | Live-incident / daily rituals, not building v1. |
| Memory & plugin tooling | claude-mem:*, cowork-plugin-management:*, plugin management | Meta-tooling unrelated to app features. |
| Advanced/meta workflow | superpowers:writing-skills, using-superpowers, using-git-worktrees, dispatching-parallel-agents, subagent-driven-development | Powerful but manual; invoke deliberately, never automatically. |
| GSD suite | gsd:* | A separate project-management workflow; not this repo's process. Use only on explicit request. |
| Doc-format exports | docx, pdf, pptx, xlsx | Only when a stakeholder asks for that exact file export. In-repo docs stay `.md`. |
| Web/scraping/infra MCP | apify:*, firecrawl_*, 21st, vercel/deploy MCPs | Unrelated to building this backend. |

---

## 6. Maintenance

When a new skill appears in the environment, add one row here with its area + priority before Claude uses it. If a skill is never triggered in practice, move it to §5. Keep this map short — routing no one reads is worse than none.
