# Command: /add-endpoint

Workflow to add a single REST endpoint correctly.

**Usage:** `/add-endpoint <METHOD /api/path — purpose>`

## Steps
1. **Load** `docs/API.md`, `.claude/rules/api.md`, `.claude/rules/security.md`, `.claude/rules/backend.md`. If the endpoint triggers the agent, also `.claude/rules/ai-agent.md`.
2. **Check the contract.** Confirm the resource, method, and status codes fit `docs/API.md`. Reuse existing DTOs/services where possible — do not duplicate.
3. **Design the contract.** Request/response records with Bean Validation; the standard error envelope; pagination if it's a list.
4. **Implement** in order: DTOs + validation → service method (with ownership/authorization check) → controller → SpringDoc/Swagger annotations.
5. **Authorize.** Confirm it is authenticated by default (or explicitly whitelisted) and enforces ownership server-side.
6. **Test.** Unit (service) + integration (Testcontainers) covering happy path, 400 (validation), and 401/403 (auth). Add a 422 test if it returns validated model output.
7. **Document.** Update `docs/API.md` and verify Swagger reflects the change.
8. **Verify.** Run `./mvnw verify`; report with evidence.

Enforces the `docs/API.md` new-endpoint checklist. Never ship an endpoint without auth, validation, tests, and doc updates.
