# ADR-0012: Tool authorization and execution-context boundary

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M5 — Tool Registry & Tool Execution Framework

## Context
When M6 arrives, an untrusted LLM will propose a tool name and arguments. The foundational security
question for M5 is: **where does security identity come from, and how is authorization enforced**, so
the model can never escalate privilege or act on another user's data. The existing system already
enforces resource ownership inside `TaskService`/`CustomerService` via `AuthorizationService`
(ownership, 404-masking, admin-any-by-id).

## Decision
- **Identity is backend-supplied.** `ToolExecutionContext` wraps the authenticated `AuthenticatedUser`
  (userId/email/roles) plus backend-generated correlation ids. It is built from the security layer,
  **never from tool arguments**. Tool inputs carry no identity field, and the executor binds arguments
  with `FAIL_ON_UNKNOWN_PROPERTIES` — so a spoofed `{"userId":…}`/`{"ownerId":…}` is a loud
  `TOOL_INVALID_INPUT`, not a silent override.
- **Two distinct authorization layers.**
  1. **Role / tool-type** — "may this user *use* this tool?" — enforced by the executor from
     `descriptor().requiredRoles()` with **any-of** semantics (like `hasAnyRole`): empty = any
     authenticated; otherwise at least one required role. Shared domain tools declare
     `{ROLE_USER, ROLE_ADMIN}` (this project's admin holds only `ROLE_ADMIN`, and REST domain routes
     also require only authentication); a future admin-only tool declares `{ROLE_ADMIN}`.
  2. **Resource / ownership** — "may this user touch this *resource*?" — delegated to the domain
     service by passing the context principal. Tools never re-implement ownership, so admin-any-by-id
     and 404-masking are preserved identically to the REST path.
- **Fail-closed, ordered gates:** resolve → authenticate → authorize (role) → bind → validate →
  execute → wrap. Nothing executes before authorization and validation pass.
- **Risk metadata now, enforcement later:** each descriptor carries a `ToolRiskLevel` and a `timeout`.
  M5 exposes them and measures duration; **hard timeout/cancellation and dangerous-op confirmation are
  deferred to M8** (wrapping transactional/thread-bound domain work in an interrupting executor would
  break `@Transactional` and the thread-local `SecurityContext`).

## Alternatives considered
- **One collapsed authorization check** — rejected: conflates "may use the tool" with "may touch the
  resource"; the second must stay in the domain service.
- **Context/identity derived from arguments** — rejected: this is precisely the privilege-escalation
  hole the design exists to close.
- **`containsAll` (all-of) role semantics** — rejected during implementation: it forbade an admin
  (who holds only `ROLE_ADMIN`) from shared tools, contradicting admin-any-by-id. Any-of matches the
  existing REST authorization model.

## Consequences
- Positive: the LLM can never manufacture identity or bypass ownership; REST and tool paths share one
  ownership implementation; the mechanism supports future admin-only tools; argument injection is
  rejected loudly.
- Negative / follow-ups: timeout is advisory in M5 (documented); confirmation for high-risk tools and
  hard cancellation land in M8.

## Links
- `docs/SECURITY.md`, `docs/TOOL_SYSTEM.md`, `docs/GUARDRAILS.md`, ADR-0011, ADR-0006 (ownership model),
  `backend/src/main/java/com/prince/agentic/tool/ToolExecutor.java`,
  `docs/superpowers/specs/2026-08-21-m5-tool-system-design.md`.
