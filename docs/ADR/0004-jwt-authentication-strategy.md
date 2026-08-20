# ADR-0004: JWT authentication strategy

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M2 — Authentication & Authorization

## Context
The API is stateless and must authenticate every protected request while producing a clean, transport-agnostic principal that future agent tools can authorize against. It must integrate with Spring Security's filter chain and the project's JSON error envelope.

## Decision
- **Library:** a single JWT library — **JJWT (`io.jsonwebtoken`) 0.12.6**. One cohesive api/impl/jackson trio; no overlapping JWT dependencies.
- **Algorithm:** **HS256** with a secret from the environment (`JWT_SECRET`). The secret must be ≥ 256 bits; `Keys.hmacShaKeyFor` fails fast on a weak secret.
- **Two distinct paths:**
  - *Login* authenticates username/password via Spring Security's `AuthenticationManager` + a DAO provider (`CustomUserDetailsService` + `BCryptPasswordEncoder`), then issues a token.
  - *Per request* a hand-written stateless `JwtAuthenticationFilter` verifies the token (signature, issuer, expiration) and builds an `AuthenticatedUser` **from verified claims — no database lookup per request.**
- **Claims:** `sub` (user id), `email`, `roles`, `iat`, `exp`, `iss`. No password, hash, or other sensitive data.
- **Principal:** `AuthenticatedUser(userId, email, roles)` — a small record decoupled from JWT/Spring, set as the authentication principal so `@AuthenticationPrincipal` and future services/tools consume one clean identity.
- **TTL:** short, `JWT_EXPIRATION_SECONDS` (default 3600); the login response returns `expiresIn` in **seconds** (this unifies the earlier minutes-vs-seconds inconsistency in the docs).
- **Errors:** a bad/missing token leaves the context unauthenticated → the entry point returns **401**; insufficient role → **403**; both render the standard `ApiError` envelope. A bad token never yields 500.

## Alternatives considered
- **Spring Security OAuth2 Resource Server (Nimbus `JwtDecoder`/`JwtEncoder`):** idiomatic and battle-tested, but yields a `Jwt`/`JwtAuthenticationToken` principal that still needs conversion to our clean `AuthenticatedUser`. The hand-written filter is small, fully controlled, and makes the identity contract explicit for the agent milestones — chosen for clarity and auditability. Revisit if we adopt asymmetric keys/JWKS or an external IdP.
- **Server-side sessions:** rejected — the API is deliberately stateless.
- **DB lookup per request:** rejected — defeats the point of stateless JWT; roles are read from the signed token.

## Consequences
- Fast, stateless authorization; horizontally scalable with no shared session store.
- **Trade-off:** authorities are as-of-issue. A role change takes effect on the user's next login (bounded by the short TTL). Documented in `docs/SECURITY.md`.
- No revocation/rotation yet — a stolen token is valid until expiry. Mitigations (short TTL now; rotation/denylist later) tracked in `docs/THREAT_MODEL.md` (T9) as future work.

## Links
`docs/SECURITY.md`, `docs/THREAT_MODEL.md`, `security/JwtService.java`, `security/JwtAuthenticationFilter.java`, `security/AuthenticatedUser.java`.
