# Non-Functional Requirements (NFRs)
## Agentic AI Task Orchestrator

> Targets, not achievements. Everything here is a design goal until implemented and MEASURED. Values marked (target) are provisional.

## 1. Security
- All non-public endpoints authenticated; RBAC + ownership enforced server-side.
- Every AI-initiated side effect authorized before execution; high-risk ops confirmed.
- No secrets in code/logs/audit; injection-resistant prompt handling.
- **Verification:** security tests (401/403, injection, authorization refusal), threat-model coverage.

## 2. Reliability
- The agent always terminates within its guardrail bounds; no unbounded loops.
- Failures are graceful and observable; partial results labeled incomplete.
- Correctness never depends on the model being right.
- **Verification:** guardrail-trip tests; failure-recovery evaluation cases.

## 3. Availability (target)
- Local/demo scope: the stack starts reliably via one command and stays up under normal demo load.
- Health checks for Postgres, Redis, and the app; dependency-aware startup ordering.
- **Verification:** healthchecks + clean-clone `docker-compose up`.

## 4. Scalability (target)
- Stateless app instances (state in Postgres/Redis) so horizontal scaling is possible later.
- Bounded per-run cost enables predictable capacity planning.
- **Verification:** load test (post-M12), documented, not assumed.

## 5. Performance (target)
- API and agent latency percentiles tracked (`PERFORMANCE.md`); targets set only after measurement.
- **Verification:** Micrometer percentiles; benchmarks marked MEASURED.

## 6. Maintainability
- Package-by-feature, clean layering, DTO boundaries, agent/domain separation.
- Coverage gate (`TESTING.md`); ADRs for significant decisions; honest, current docs.
- **Verification:** code review against `.claude/rules/*`, coverage gate, doc-truth checks.

## 7. Observability
- Correlation/execution/tool ids on every relevant log, metric, and audit event.
- Key metrics for API, agent, tools, and LLM; dashboards.
- **Verification:** id-propagation and metric tests.

## 8. Testability
- Deterministic tests; LLM/tools mockable; Testcontainers infra; reproducible evaluation.
- **Verification:** no live-network tests; CI green.

## 9. Auditability
- Durable, queryable trail of who-did-what for every significant agent action (`AUDIT_LOGGING.md`).
- **Verification:** audit-record tests; admin retrieval RBAC-gated.

## 10. Portability
- Runs from a clean clone via Docker Compose; config via env vars; no hardcoded hostnames.
- **Verification:** clean-clone startup.

## 11. Deployability
- One-command stack; forward-only migrations; documented env vars; green CI.
- **Verification:** `engineering:deploy-checklist` + `RELEASE_CHECKLIST.md`.

## 12. Data privacy
- Local-first; external providers opt-in and privacy-reviewed; redaction of sensitive data (`DATA_PRIVACY.md`).
- **Verification:** no-secret/PII-in-logs tests; fallback-gating tests.

---

Each NFR has a verification method. An NFR is only "met" when its verification passes on implemented code — labeled accordingly.
