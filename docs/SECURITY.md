# Security Model
## Agentic AI Task Orchestrator

> Conceptual model. Auth is planned (M2). Nothing is implemented yet. The LLM is treated as an untrusted planner, never a security boundary.

## 1. Authentication

- Stateless **JWT** (short TTL, e.g. `JWT_EXPIRATION_MINUTES=30`), signed with `JWT_SECRET` from the environment.
- Passwords hashed with **BCrypt**; never stored or logged in plaintext.
- Public routes only: `/api/auth/**`, `/actuator/health`, Swagger. Everything else requires a valid token.

## 2. Authorization (RBAC + ownership)

- Roles: **USER** (owns tasks/customers) and **ADMIN** (manages users, inspects executions/audit).
- Method security: `@PreAuthorize("hasRole('ADMIN')")` on `/api/admin/**`.
- **Ownership** is enforced in services and tools: a USER may only read/modify their own resources, checked server-side against the authenticated principal — never against a client- or model-supplied claim.

## 3. Authorizing AI-initiated actions (the core problem)

The agent acts *on behalf of* a user, but its proposed actions come from an untrusted model. Therefore:

- Every side-effecting tool re-derives the authenticated user and **authorizes before executing** (`TOOL_SYSTEM.md` §3).
- The agent is offered only the tools permitted in the current context (least privilege).
- The agent can never exceed the user's own permissions.
- High-risk actions require explicit confirmation (`GUARDRAILS.md`).

## 4. Input & file validation

- All request DTOs validated with Bean Validation; violations → `400` with field messages.
- All model-generated tool arguments validated against their schema before use.
- File uploads (if/when added) validated by content type + magic bytes + size before processing — never by extension alone.

## 5. Secrets & configuration

- All secrets from environment variables; only `.env.example` is committed.
- No secret, token, password, or key in code, logs, audit records, or error messages.

## 6. Transport & web concerns

- **CORS:** restrict allowed origins to the known frontend origin(s) via config; do not use a wildcard with credentials.
- **CSRF:** the API is stateless and token-authenticated (no cookie-based sessions), so CSRF protection is handled by that model; document it explicitly rather than blindly disabling protections. If any cookie-based flow is introduced, add CSRF tokens.
- **Rate limiting:** cap auth attempts and agent invocations per user/time to limit abuse and cost (`GUARDRAILS.md`).

## 7. AI-specific safety

- **Prompt injection:** all untrusted text (user input, tool outputs re-fed to the model, external data) is delimited and can never override system instructions.
- **Tool abuse:** bounded execution + authorization + confirmation.
- **Output grounding:** model output is validated; unsupported claims are dropped; raw model text is never an API result.
- **External providers:** sensitive data is not sent to an external model unless fallback is explicitly enabled and privacy-reviewed (`DATA_PRIVACY.md`).

Full threat catalogue with mitigations, likelihood, detection, and tests: `THREAT_MODEL.md`.

## 8. Error handling (security-relevant)

No stack traces or internal detail in API responses; all errors go through the global handler and the standard envelope (`ERROR_HANDLING.md`). Authentication/authorization failures are logged (without secrets) and auditable.

## 9. On every new endpoint or tool

Authenticated by default? Ownership/authorization enforced before effect? Input/arguments validated? Errors routed through the handler? Dangerous op gated by confirmation? Security test for the 401/403 path added? If any answer is "no", it is not done.
