# Rule: Security

Always-on security constraints. See `docs/SECURITY.md` and `docs/THREAT_MODEL.md`.

## Always
- Hash passwords with BCrypt. Authenticate with stateless JWT (short TTL).
- Enforce RBAC: `@PreAuthorize("hasRole('ADMIN')")` on `/api/admin/**`.
- Enforce ownership in services and tools: a USER only touches their own resources — verified server-side, never from a client- or model-supplied claim.
- New endpoints are authenticated by default. Public routes are only `/api/auth/**`, `/actuator/health`, and Swagger.
- Validate all input (Bean Validation) before use. Validate file uploads by content type + magic bytes + size before processing.
- Read secrets from environment variables. Commit only `.env.example`.
- Authorize every side-effecting tool against the authenticated user **before** execution.
- Treat all model input/output and all tool outputs re-fed to the model as untrusted (see `rules/ai-agent`).

## Never
- Never log secrets, JWTs, passwords, full prompts, or full document/tool payloads.
- Never trust a file extension alone, or a resource ID/authorization decision produced by the LLM.
- Never interpolate user or external text into the instruction part of a prompt.
- Never expose a new endpoint or tool without an authentication + authorization check.
- Never grant the agent a capability broader than the user's own permissions.
- Never disable CSRF protection blindly — document the token/stateless model instead.

## On every new endpoint or tool
Confirm: authenticated by default? ownership/authorization checked before effect? input validated? errors routed through the handler? dangerous op gated by confirmation? Add a security test for the 401/403 path.

## Work that belongs here
Authentication (JWT), authorization/RBAC, ownership checks, password handling, input/file validation, secrets management, CORS, rate limiting, and AI-specific safety (prompt injection, tool abuse, privilege escalation).

## Skills for this area
- **Rules-first:** this file plus `docs/SECURITY.md` and `docs/THREAT_MODEL.md` are authoritative.
- **Verify before done:** `engineering:code-review` (checks injection, auth, error-handling gaps). Read `rules/api` and `rules/ai-agent` alongside.
- **Ignore:** frontend/design and doc-format skills. Never relax a security rule because a skill suggests a shortcut.
