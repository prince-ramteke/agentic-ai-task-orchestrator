# Definition of Done
## Agentic AI Task Orchestrator

A change is **not** done because it compiles. It is done when every applicable item below is true, with evidence.

## Universal checklist (every change)

- [ ] **Requirements understood** — the objective and its edge cases are clear (asked, not assumed).
- [ ] **Docs & rules read** — the relevant `docs/*.md` and `.claude/rules/*.md` were consulted first.
- [ ] **Skills used** — the right skill(s) per `SKILL_ROUTING_MAP.md`, fewest that fit.
- [ ] **Architecture respected** — layering, agent/domain separation, and boundaries preserved; ADR added if significant.
- [ ] **No duplication** — existing APIs/models/tools checked before adding new ones.
- [ ] **Implementation complete** — no TODO stubs presented as finished.
- [ ] **Input validated** — request DTOs and model-generated tool arguments validated.
- [ ] **Errors handled** — routed through the global handler; no internals leaked.
- [ ] **Security reviewed** — auth by default, ownership/authorization before effect, no secrets, injection-safe (`.claude/rules/security.md`, `ai-agent.md`).
- [ ] **Observability/audit considered** — correlation/execution ids, metrics, and audit for side effects.
- [ ] **Tests added** — unit + integration (+ agent/eval where relevant) covering happy + error + auth paths, with real assertions.
- [ ] **Build & tests green** — `./mvnw verify` passes at the coverage gate. **Evidence attached.**
- [ ] **Docs updated** — `docs/*.md`, Swagger, README status, `CHANGELOG.md` reflect the change; honesty labels correct.
- [ ] **No secrets committed** — `.env.example` updated if config changed.
- [ ] **Diff reviewed** — self-review done; unrelated changes excluded.
- [ ] **Trade-offs & risks stated** — regressions considered; backward compatibility preserved where practical.

## Extra items by change type

- **New endpoint:** DTOs+validation, ownership check, Swagger, `API.md`, tests for 400/401/403 (+422 if model output).
- **New tool:** full tool contract (`TOOL_SYSTEM.md`): typed I/O, validation, authorization, side-effect class, timeout, retry, audit; tests for argument-validation + authorization refusal (+ confirmation for high-risk); `TOOL_SYSTEM.md` updated; evaluation cases added.
- **Prompt / tool-selection change:** treated as a behavior change — evaluation suite re-run; no unjustified regression (`EVALUATION.md`).
- **Schema change:** Flyway migration (not an edit), `DATABASE.md` updated, migration applies on a clean DB.
- **Guardrail change:** bound enforced by code, tested for tripping; `GUARDRAILS.md` updated.
- **New env var:** `.env.example` + `DEPLOYMENT.md` updated.
- **Milestone:** every roadmap output present; milestone validation run; `/ship-milestone` checklist passed.

## The honesty rule

Never claim done without running the build/tests. Never state a feature/metric exists unless it does. Label everything PLANNED / IMPLEMENTED / TESTED / VERIFIED / MEASURED. **Evidence before assertions.**
