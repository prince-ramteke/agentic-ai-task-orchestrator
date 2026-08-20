# Release Checklist
## Agentic AI Task Orchestrator

> Run before shipping a milestone/version. Applies from Milestone 12; earlier milestones use the subset that exists. Nothing ships on assertion — each item needs evidence.

## Build & test
- [ ] `./mvnw verify` green (tests + coverage gate). Evidence captured.
- [ ] Frontend build/test green (from M13).
- [ ] CI green on the branch; branch protection satisfied.

## Agent quality (agent milestones)
- [ ] Evaluation suite run; per-dimension scores recorded and non-regressed (`EVALUATION.md`).
- [ ] Guardrail tests pass (bounds, loop detection, confirmation).

## Security
- [ ] `/security-review` findings resolved; 401/403 + injection + authorization-refusal tests pass.
- [ ] No secrets in code/git; `.env` not committed.
- [ ] External model fallback off (or explicitly enabled + privacy-reviewed).

## Configuration
- [ ] Every required env var present and documented in `.env.example` + `DEPLOYMENT.md`.
- [ ] No hardcoded hostnames/credentials; profile config correct.

## Database
- [ ] Flyway migrations apply cleanly on a fresh database.
- [ ] No edits to already-applied migrations.

## Stack
- [ ] Clean-clone `docker-compose up --build` brings up all services with healthchecks + correct ordering.
- [ ] Health endpoints green.

## Observability
- [ ] Metrics scraped by Prometheus; Grafana dashboards load.
- [ ] Correlation/execution ids correlate a run end-to-end.
- [ ] Audit records produced for side-effecting actions; no secrets/PII in them.

## Documentation
- [ ] `README.md` status table current; no unfinished feature claimed done.
- [ ] All affected `docs/*.md` + Swagger updated; honesty labels correct.
- [ ] ADRs recorded for significant decisions.
- [ ] `CHANGELOG.md` updated with a real, dated entry.
- [ ] Release notes drafted.

## Git & final
- [ ] `git status` clean; only intended changes committed.
- [ ] No secrets in the diff or history for this release.
- [ ] Milestone Definition of Done satisfied (`DEFINITION_OF_DONE.md`).

## Sign-off
- [ ] Report: what shipped (IMPLEMENTED/TESTED/VERIFIED/MEASURED), what remains PLANNED, evidence, risks, recommended next milestone.
