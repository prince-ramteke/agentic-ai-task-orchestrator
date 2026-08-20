# Prompt: Prepare a release

Use to prepare a milestone/version for shipping. _(Applies from Milestone 12.)_

---

**Release:** <milestone / version>

## Do this in order
1. **Invoke** `engineering:deploy-checklist`. Load `docs/RELEASE_CHECKLIST.md` and `docs/DEFINITION_OF_DONE.md`.
2. **Build & test.** `./mvnw verify` green; frontend build/test green; CI green.
3. **Evaluate** (agent milestones). Run the evaluation suite (`docs/EVALUATION.md`); record scores.
4. **Config & secrets.** All env vars documented in `.env.example` + `docs/DEPLOYMENT.md`; no secrets committed; no hardcoded hostnames.
5. **Stack.** Clean-clone `docker-compose up --build` brings up all services with healthchecks; Flyway migrations apply on a fresh DB.
6. **Observability.** Health endpoints up; metrics scraped; dashboards load.
7. **Docs.** README status table, all affected `docs/*.md`, Swagger, ADRs, and `docs/CHANGELOG.md` updated and truthful (correct PLANNED/IMPLEMENTED/TESTED/VERIFIED/MEASURED labels).
8. **Run the Release Checklist** item by item.
9. **Report** readiness with evidence, or the exact blockers, plus a short release-notes summary.

## Constraints
Nothing ships on assertion. Every "done" item has evidence. No fabricated metrics or completed-feature claims.
