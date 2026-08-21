# ADR-0006: Core Domain Ownership Model

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince

## Context

Milestone 3 introduces the first user-owned business resources (`Task`, `Customer`). The security
model established in M2 (`AuthenticatedUser`, `AuthorizationService`, deny-by-default) must remain
authoritative, and — critically — the domain services created here must be safely callable by the
AI tools of a later milestone (M5). That means ownership and authorization must be enforced in the
service layer, server-side, never trusting a client- or model-supplied claim. We must also avoid
Insecure Direct Object Reference (IDOR): a USER must not read, modify, or even *detect the
existence of* another user's resource.

## Decision

- **Ownership is a plain `Long ownerId` column** on each aggregate (not a `@ManyToOne User`
  association). The domain never navigates to the owning `User`; it only authorizes against the id.
  The database enforces integrity with a real foreign key and `ON DELETE CASCADE`. The column is
  `updatable = false` — a resource's owner is set once and never reassigned.
- **Owner is assigned server-side** from `AuthenticatedUser.userId()`. Create DTOs have **no**
  `ownerId` field, so ownership cannot be mass-assigned; any `ownerId` in a request body is an
  unknown property and is ignored.
- **Authorization reuses `AuthorizationService.canAccess(user, ownerId)`** — the single M2 authority.
  By-id operations use load-then-authorize: load by id (404 if truly absent), then `canAccess`
  (404 for a non-owner USER — existence-masking; admin bypass is already encoded in `canAccess`).
- **Non-owner / missing single resource → 404** (never 403), to avoid leaking which ids exist.
  403 remains reserved for RBAC/role denial (e.g. a USER hitting an ADMIN-only route).
- **ADMIN policy: own-list + admin-any-by-id.** List endpoints return only the caller's own
  resources for USER *and* ADMIN. An ADMIN may GET/PUT/DELETE any single resource by id (ownership
  bypassed), but **never** bypasses input validation. Cross-user admin *listing* is deferred to a
  dedicated admin API milestone.

## Alternatives considered

- **`@ManyToOne User owner`:** rejected — introduces unnecessary navigation, lazy-proxy handling
  (with `open-in-view=false`), and N+1 risk, for no benefit since only the id is needed.
- **403 for a non-owner:** rejected — leaks resource existence; `API.md` §2 mandates 404-masking.
- **ADMIN list returns all users' resources:** rejected for M3 — an admin's normal list would page
  the whole table and responses would surface other users' data; deferred to an explicit admin API.
- **`?ownerId=` filter for admins:** rejected for M3 — adds query surface/validation without an M3 need.

## Consequences

- Domain services are self-contained, tool-safe business boundaries; the DB guarantees referential
  integrity and cascade cleanup. IDOR and ownership-spoofing are structurally prevented.
- A future admin API milestone will add cross-user listing deliberately; `canAccess` stays the one
  ownership authority the AI-tool layer will also call.
- Because a non-owner sees 404, tests assert 404 (not 403) for cross-user access to owned resources.

## Links

- Spec: `docs/superpowers/specs/2026-08-21-m3-core-domain-design.md`
- `docs/SECURITY.md` §11 (agent-future security contract), `docs/API.md` §2 (status codes), ADR-0003.
