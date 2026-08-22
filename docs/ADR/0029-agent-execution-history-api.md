# ADR-0029 — Agent Execution History API

**Status:** Accepted · **Milestone:** M9 · **Date:** 2026-08-22

## Context
Users need to read their agent execution history. The API must be owner-scoped, paginated, sanitized,
and must not become an ad-hoc query language or leak sensitive content.

## Decision
- **Two read-only endpoints** under `/api/v1/agent`:
  `GET /executions` (paginated list) and `GET /executions/{executionId}` (detail with ordered steps +
  tool executions). No write endpoints — audit is append-only via the internal listener.
- **Owner-scoped in SQL**; a foreign or missing execution id → masked **404 `EXECUTION_NOT_FOUND`**
  (existence-masking). Identity from `@AuthenticationPrincipal`; `conversationId` is a filter, never an
  authorization claim.
- **Bounded filters only:** `status`, `conversationId`, `from`/`to` (ISO-8601 instants), `toolName`;
  pagination via the shared `SortWhitelist` (sortable: `startedAt`/`completedAt`/`status`; default
  `startedAt DESC`; size clamped). No arbitrary filter language.
- **Admin scope deferred:** USER and ADMIN both see only their own executions in M9; an explicit
  RBAC-gated admin cross-user endpoint is a documented later decision (no cross-user data path ships).
- **Sanitized DTOs:** never internal class names, raw prompts, arguments, LLM output, chain-of-thought,
  stack traces, or secrets.

## Consequences
- Safe, predictable history retrieval; secure-by-default (no cross-user path).
- Verified by `AgentAuditControllerTest` (shapes, 404 masking, 401) and `AgentAuditE2EIT` (owner
  isolation end-to-end).
