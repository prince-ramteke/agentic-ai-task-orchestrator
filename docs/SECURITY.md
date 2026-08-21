# Security Model
## Agentic AI Task Orchestrator

> **Milestone 4 status (AI layer):** `/api/v1/ai/**` is authenticated by the existing deny-by-default
> policy (no `PUBLIC_ENDPOINTS` change; a 401 test guards it). Input is bounded and validated
> (`@NotBlank`, ≤4000 → 400). The AI layer performs **no** database access, ownership decisions, or
> tool execution, and grants the caller no capability beyond an LLM call. **Model output is untrusted**
> — structured output is re-validated (Bean Validation) before use, and no raw provider object is
> returned. Prompts are not a security boundary (delimited-input is defense-in-depth only); deeper
> prompt-injection/guardrail handling is deferred to M8. No secrets are logged or sent to any model.
>
> **Milestone 2 status: authentication & authorization IMPLEMENTED and VERIFIED.** The core
> auth boundary (JWT, BCrypt, RBAC, deny-by-default) is live and tested. Ownership enforcement
> on domain resources and AI-tool authorization are foundations here and are wired up in later
> milestones (M3+/M5+). The LLM is treated as an untrusted planner, never a security boundary.

## 1. Authentication — IMPLEMENTED

- Stateless **JWT (HS256)**, short TTL via `JWT_EXPIRATION_SECONDS` (default 3600), signed with
  `JWT_SECRET` from the environment (must be ≥ 256 bits; the app fails fast on a weak secret).
- Passwords hashed with **BCrypt** (`BCryptPasswordEncoder`); the raw password exists only
  transiently and is never stored or logged.
- Login authenticates via Spring Security's `AuthenticationManager` + a DAO provider
  (`CustomUserDetailsService`); per-request auth is a stateless filter that verifies the token
  and builds an `AuthenticatedUser` from claims — **no DB lookup per request** (ADR-0004).
- Public routes only: `/api/v1/auth/**`, `/api/v1/health`, `/actuator/health`, `/actuator/info`,
  and the Swagger/OpenAPI paths. **Everything else requires a valid token (deny by default).**

## 2. Authorization (RBAC + ownership) — IMPLEMENTED (RBAC) / FOUNDATION (ownership)

- Roles: **ROLE_USER** (default for every registered user) and **ROLE_ADMIN** (server-assigned).
  Roles are persisted (`roles` + `user_roles`) and seeded by Flyway (ADR-0003).
- Method security: `@PreAuthorize("hasRole('ADMIN')")` guards `/api/v1/admin/**`
  (demonstrated by `GET /api/v1/admin/ping`). USER → 403, ADMIN → 200, anonymous → 401.
- **The authenticated principal** is `AuthenticatedUser(userId, email, roles)`, resolved from the
  verified token — the single identity future services and agent tools authorize against.
- **Ownership** is enforced on concrete resources as of **M3** (`Task`, `Customer`) via
  `AuthorizationService.canAccess(user, ownerId)` — the reusable, server-side check that a USER may
  only touch their own resources (ADMIN may act per permission), verified against the authenticated
  principal, never a client/model claim. Owner is assigned server-side from the token; create DTOs
  have no `ownerId` field, so ownership cannot be mass-assigned (ADR-0006).
- **404 vs 403 on domain resources:** a USER requesting another user's (or a non-existent) resource
  by id receives **404** (existence-masking — the API never reveals which ids exist). **403** is
  reserved for RBAC/role denial (e.g. a USER hitting an ADMIN-only route). List endpoints are
  `owner_id`-scoped in SQL for USER and ADMIN alike; an ADMIN may act on any single resource by id.
- **The client can never choose a role:** `RegisterRequest` has no role field; public
  registration always grants `ROLE_USER`; ADMIN is a controlled server-side assignment only.

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

## 10. Implemented details (M2)

**Endpoints.** Public: `POST /api/v1/auth/register` (→ 201, always `ROLE_USER`), `POST /api/v1/auth/login` (→ 200 `{accessToken, tokenType:"Bearer", expiresIn}`). Protected: `GET /api/v1/me` (any authenticated user), `GET /api/v1/admin/ping` (ADMIN only).

**Token lifecycle & claims.** HS256, issuer `agentic-ai-task-orchestrator`, TTL `JWT_EXPIRATION_SECONDS`. Claims: `sub` (user id), `email`, `roles`, `iat`, `exp`, `iss`. No password/hash/secret is ever placed in a token. Verification checks signature, issuer, and expiration.

**401 vs 403.**
- **401 UNAUTHORIZED** — missing/invalid/expired/malformed/forged token on a protected route (via `RestAuthenticationEntryPoint`).
- **403 FORBIDDEN** — authenticated but lacking the required role (via `@PreAuthorize` → global handler, and `RestAccessDeniedHandler` for filter-level denials).
- Both render the standard `ApiError` envelope; a bad token never yields 500.

**Secrets & config.** `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`, `DATABASE_*`, `CORS_ALLOWED_ORIGINS` come from the environment (`.env.example`). Only a clearly-labeled **test-only** JWT secret is committed (in `application-test.yml`), never a usable production secret.

**CORS / CSRF.** CORS is restricted to configured origins (no wildcard-with-credentials). CSRF protection is disabled **deliberately** because the API is stateless and token-based with no cookie session; if any cookie-based flow is added, CSRF tokens must be reintroduced.

**Account enumeration.** Login failures (unknown user, wrong password, disabled account) all return an identical generic `401 INVALID_CREDENTIALS` — the response never reveals whether an account exists.

**Brute force / rate limiting.** *Not implemented in M2.* Current mitigations are BCrypt (slow hashing) + short token TTL + generic errors. Per-IP/per-account login rate limiting is future work (`THREAT_MODEL.md` T9/T10, `GUARDRAILS.md`).

**Known limitations (honest).**
- Token authorities are as-of-issue; a role change applies on next login (bounded by the short TTL).
- No token revocation/rotation/denylist yet — a stolen token is valid until expiry.
- Ownership enforcement is applied to concrete resources as of M3 (`Task`, `Customer`), verified by unit + API + real-PostgreSQL tests (IDOR, mass-assignment, admin-any-by-id, 404-masking).

## 11. The agent-future security contract (must not regress)

When the agent arrives (M6+), the LLM proposes actions; it is **never** trusted to authorize them. Every tool execution follows:

```
LLM proposes a tool call
      ↓
Backend resolves the authenticated user (AuthenticatedUser, from the verified token — not the model)
      ↓
Tool authorization check   (is this user allowed to use this tool in this context?)
      ↓
Resource ownership check   (AuthorizationService.requireOwnershipOrAdmin — server-side)
      ↓
Tool argument validation   (typed schema; never trust model-supplied ids/args)
      ↓
Execute (+ audit)
```

The agent can never exceed the authenticated user's own permissions, and admin tools are never offered in a USER context. M2 establishes the identity (`AuthenticatedUser`) and ownership primitive (`AuthorizationService`) this contract depends on; the tool-authorization engine is built in M5.
