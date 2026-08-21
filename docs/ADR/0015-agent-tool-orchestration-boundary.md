# ADR-0015: Agent / Tool Orchestration Boundary

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince + Claude

## Context

M6 is the first milestone where the model can cause effects. The security of the whole system depends
on the model never reaching data or infrastructure except through the M5 tool boundary, and on the
agent never being stronger than the authenticated user. The API shape and error model also need to
fit the existing `/api/v1` conventions and the standard `ApiError` envelope without lying about
partial/failed runs.

## Decision

- **Effects only via M5.** `AgentOrchestrator` reaches data/effects **only** through
  `ToolExecutor.execute(name, arguments, context)`. It never imports a repository, `EntityManager`,
  `JdbcTemplate`, or a domain service, and never uses Spring AI directly (model access stays behind
  `LlmClient`). This is enforced by `AgentArchitectureBoundaryTest`, a source scan of
  `com.prince.agentic.agent` (M5's `ToolArchitectureBoundaryTest` is the template). The agent package
  *may* import `com.prince.agentic.ai.llm` (the approved LLM abstraction) — that is the one AI import
  allowed.
- **Identity is backend-supplied.** The `ToolExecutionContext` is built from the
  `@AuthenticationPrincipal AuthenticatedUser`; the request DTO carries only `message`. The model
  cannot set `userId`/`roles`/`ownerId`/`executionId`.
- **Endpoint:** `POST /api/v1/agent/execute` (authenticated, deny-by-default), consistent with
  `/api/v1/ai`. This supersedes the earlier `ROADMAP.md` placeholder `POST /api/agent/chat`.
- **Two-tier errors.** A started run that terminates in a controlled state (FAILED / LIMIT_REACHED /
  LOOP_DETECTED / TIMED_OUT / CANCELLED) returns **HTTP 200** `AgentExecuteResponse` with a stable
  `failureCode`, because the run carries metadata (iterations, toolCalls, durationMs) an `ApiError`
  envelope cannot. Pre-execution request/auth faults use the standard envelope (400/401).
  `AgentInvalidDecisionException extends ApiException` (422) exists for callers outside the loop.

## Alternatives considered

- **Non-2xx envelope for failed runs** — rejected: it discards run metadata and conflates a bounded,
  observable terminal state with a pre-execution fault. The 200 + `failureCode` model mirrors the M5
  `ToolExecutor` philosophy of returning a structured outcome rather than throwing on the run path.
- **`/api/agent/chat`** — rejected: inconsistent with the versioned `/api/v1` namespace.
- **Letting the orchestrator call `TaskService` directly for "agent-level" rules** — rejected: it
  would bypass the authorization/validation the tool boundary guarantees.

## Consequences

- The agent is provably ≤ the user's permissions (ownership stays enforced by M3 services via M5).
- `ROADMAP.md` and `API.md` are updated for the endpoint and error model.
- M8's confirmation/rate-limit layer attaches at the single tool-execution point without new data paths.

## Links

- Spec §7, §15, §17, §20 (R1, R3), §21. ADR-0011/0012 (tool abstraction/authorization), ADR-0014.
