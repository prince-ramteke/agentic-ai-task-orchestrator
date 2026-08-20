# Command: /new-feature

Workflow to add a complete vertical feature slice with discipline.

**Usage:** `/new-feature <feature name>`

## Steps
1. **Load context.** Read `CLAUDE.md`, the relevant `docs/*.md` (at minimum the one that owns this feature's contract), and every applicable `.claude/rules/*.md`.
2. **Classify & route.** Determine the task type and consult `docs/SKILL_ROUTING_MAP.md`. Start with `superpowers:brainstorming` → `superpowers:writing-plans`.
3. **Inspect existing architecture and code.** Read a sibling feature package to match patterns. Check for existing APIs/models/tools before introducing duplicates.
4. **Decide if an ADR is needed.** If the change is architecturally significant, draft an ADR (`docs/ADR/`) or flag that one is required.
5. **Present the plan for significant work** using the required template (Understanding / Architecture Impact / Files Affected / Implementation Plan / Testing Plan / Risks). Wait for confirmation before coding when the change is significant.
6. **Open `.claude/prompts/build-feature.md`** and follow it end to end for `<feature name>`.
7. **Fill test gaps** with `.claude/prompts/write-tests.md`.
8. **Self-review** with `.claude/prompts/review-code.md` (and `review-security.md`/`review-agent-behavior.md` if the feature touches auth or the agent).
9. **Update docs** (`docs/API.md`/Swagger, `docs/DATABASE.md`, `docs/TOOL_SYSTEM.md`, `docs/CHANGELOG.md`, `README.md` status) for anything that changed.
10. **Verify & report.** Run `./mvnw verify`; report only with evidence (test/build summary), changed files, trade-offs, and risks.

Enforces: plan-first · docs-as-truth · agent/security review when relevant · tests + review before "done".
