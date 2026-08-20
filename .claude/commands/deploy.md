# Command: /deploy

Pre-ship verification for the containerized stack. _(Applies from Milestone 12; earlier, most steps are N/A.)_

**Usage:** `/deploy <environment>`

## Steps
1. **Load** `docs/DEPLOYMENT.md`, `docs/RELEASE_CHECKLIST.md`, `.claude/rules/deployment` (if present) and `.claude/rules/security.md`. Invoke `engineering:deploy-checklist`.
2. **Build & test.** `./mvnw verify` green; frontend build/test green; CI green on the branch.
3. **Config.** Every required env var present and documented in `.env.example` + `docs/DEPLOYMENT.md`. No secrets committed. No hardcoded hostnames.
4. **Stack.** `docker-compose up --build` starts postgres, redis, ollama, backend, (frontend), prometheus, grafana with healthchecks and correct `depends_on` ordering from a clean clone.
5. **Migrations.** Flyway migrations apply cleanly on a fresh database.
6. **Observability.** Health endpoints up; metrics scraped; dashboards load.
7. **Run the Release Checklist** (`docs/RELEASE_CHECKLIST.md`) item by item.
8. **Report** readiness with evidence, or the exact blockers.

Never mark deploy-ready without a clean-clone `docker-compose up` and a green checklist.
