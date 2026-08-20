# Rule: Documentation

Always-on constraints for keeping docs truthful. See `docs/DOCUMENTATION.md`.

## Always
- Treat `docs/*.md` as the source of truth. If code and a doc disagree, fix one — deliberately — in the same change.
- Update the relevant doc when behavior or contract changes:
  - New/changed endpoint → `docs/API.md` (+ Swagger).
  - Schema change → `docs/DATABASE.md` (+ Flyway migration).
  - New/changed tool → `docs/TOOL_SYSTEM.md`.
  - New guardrail/bound → `docs/GUARDRAILS.md`.
  - New dependency/decision → `docs/TECH_STACK.md` (+ an ADR if significant).
  - New env var → `docs/DEPLOYMENT.md` + `.env.example`.
  - Milestone progress → `docs/ROADMAP.md` + `docs/CHANGELOG.md`.
- Keep `README.md` honest and demo-ready, with the project-status table current.
- Label every claim as PLANNED / IMPLEMENTED / TESTED / VERIFIED / MEASURED. Never blur them.
- Write comments that explain *why*, not *what*. Delete stale comments.

## Never
- Never ship a behavior change that leaves a doc lying.
- Never document a planned feature as if it already exists.
- Never claim a metric was measured or a test passed unless it actually was.
- Never leave the agent/API contract undocumented after a change.

## Work that belongs here
README, PRD, charter, architecture, roadmap, ADRs, changelog, runbooks, and keeping `docs/*.md` truthful.

## Skills for this area
- **Auto-consult:** `engineering:documentation`.
- **Manual only:** doc-format skills (`docx`, `pdf`, `pptx`, `xlsx`) — only when a stakeholder asks for that exact export. In-repo docs stay `.md`.
- **Ignore:** engineering/design build skills. Docs describe the system; they don't build it.
