# Documentation Standards
## Agentic AI Task Orchestrator

How we keep documentation truthful and useful. Docs are the source of truth; code must not silently diverge from them.

## 1. The doc map

| Doc | Owns |
|---|---|
| `PRD.md` / `PROJECT_CHARTER.md` | Why it exists, users, scope, goals |
| `ROADMAP.md` | Milestones, sequencing, per-milestone DoD |
| `SYSTEM_ARCHITECTURE.md` / `AGENT_ARCHITECTURE.md` | Components, flows, agent lifecycle |
| `TOOL_SYSTEM.md` / `GUARDRAILS.md` / `MEMORY.md` | Agent capability, bounds, state |
| `API.md` | Endpoint contract (with Swagger) |
| `DATABASE.md` | Schema, entities, migrations |
| `SECURITY.md` / `THREAT_MODEL.md` / `DATA_PRIVACY.md` / `AUDIT_LOGGING.md` | Security & safety |
| `OBSERVABILITY.md` / `PERFORMANCE.md` / `NON_FUNCTIONAL_REQUIREMENTS.md` | Ops & quality attributes |
| `TESTING.md` / `EVALUATION.md` | Verification |
| `CODING_STANDARDS.md` / `ERROR_HANDLING.md` | How code is written |
| `TECH_STACK.md` / `ADR/` | Technology choices & decisions |
| `DEPLOYMENT.md` / `RELEASE_CHECKLIST.md` | Shipping |
| `DEFINITION_OF_DONE.md` | When work is complete |
| `CHANGELOG.md` | What changed, when |
| `SKILL_ROUTING_MAP.md` | Which skill for which task |

## 2. Update rules (what changes which doc)

- New/changed endpoint → `API.md` + Swagger.
- Schema change → `DATABASE.md` + a Flyway migration.
- New/changed tool → `TOOL_SYSTEM.md`.
- New guardrail/bound → `GUARDRAILS.md`.
- New dependency/decision → `TECH_STACK.md` (+ ADR if significant).
- New env var → `DEPLOYMENT.md` + `.env.example`.
- Milestone progress → `ROADMAP.md` + `CHANGELOG.md` + README status table.

## 3. The honesty labels (mandatory)

Every capability claim carries one of: **PLANNED · IMPLEMENTED · TESTED · VERIFIED · MEASURED**. Never blur them. A feature is not "implemented" until the code exists; not "tested" until tests pass; not "measured" until benchmarked on this system.

## 4. Writing style

- Actionable over generic. Not "security is important" but "every side-effecting tool authorizes the user before execution".
- Structured (headings, tables, short lists). Concise where possible, detailed where architecture demands.
- Diagrams (Mermaid) where they clarify a flow.
- Comments in code explain *why*; docs explain the *what/how* of the system.

## 5. Consistency

- Same terms everywhere (execution id, tool, guardrail, side-effecting, ownership).
- Cross-link related docs rather than duplicating content; fix the single owner when a fact changes.

## 6. Skills

`engineering:documentation` for structure/clarity. Doc-format skills (`docx`/`pdf`/`pptx`/`xlsx`) are manual, only for a stakeholder-requested export — in-repo docs stay `.md`.
