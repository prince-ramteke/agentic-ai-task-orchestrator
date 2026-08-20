# Prompt: Review API

Use to review an endpoint or contract change.

---

**Under review:** <endpoint(s) / contract change>

## Checklist
1. **Contract fit.** Nouns for resources; correct method and status codes; matches `docs/API.md`. No break to a published response shape without versioning.
2. **Auth.** Authenticated by default (or explicitly whitelisted); ownership enforced in the service before effect.
3. **Validation.** Request DTO validated with Bean Validation; `400` with field messages on violation.
4. **Response.** DTOs only — no entities or internal fields (password hash, secrets, raw model text). Standard error envelope for errors.
5. **Lists.** Paginated (`page`, `size`, `sort`); filtering/sorting documented.
6. **Idempotency.** Retryable unsafe operations handled where it matters.
7. **Docs.** `docs/API.md` and Swagger updated and accurate.
8. **Tests.** Happy + 400 + 401/403 (+ 422 if returning validated model output) exist.

## Output
Findings by severity with file:line and fixes; explicitly confirm whether `docs/API.md` and Swagger match the code.
