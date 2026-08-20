# Command: /documentation-update

Bring docs back in sync with reality after a change.

**Usage:** `/documentation-update <what changed>`

## Steps
1. **Load** `.claude/rules/documentation.md`. Invoke `engineering:documentation`.
2. **Find the affected docs** using the mapping in `.claude/rules/documentation.md` (API → `API.md`+Swagger; schema → `DATABASE.md`; tool → `TOOL_SYSTEM.md`; guardrail → `GUARDRAILS.md`; env var → `DEPLOYMENT.md`+`.env.example`; milestone → `ROADMAP.md`+`CHANGELOG.md`).
3. **Verify against code.** Read the actual implementation; do not document intentions as facts. Correct any drift in either direction.
4. **Label status.** Mark each claim PLANNED / IMPLEMENTED / TESTED / VERIFIED / MEASURED accurately.
5. **Update the README status table** and `docs/CHANGELOG.md`.
6. **Cross-check links** and naming consistency across docs.
7. **Report** which docs changed and confirm no doc now contradicts the code.

Never leave a doc lying after a behavior change. Never invent metrics or completed features.
