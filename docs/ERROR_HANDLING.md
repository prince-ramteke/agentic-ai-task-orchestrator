# Error Handling
## Agentic AI Task Orchestrator

> Conceptual. The global handler is planned (M1). Errors are meaningful to developers, safe for users, observable, and appropriately logged.

## 1. Principles

- **One place:** all errors flow through a global `@RestControllerAdvice`. Controllers/services throw domain exceptions; the handler maps them to HTTP + the standard envelope (`API.md` §3).
- **Safe outward, rich inward:** clients get a human-safe message + `traceId`; full detail (stack trace, cause) goes to logs keyed by that id.
- **Never swallow:** a caught exception is handled, rethrown, or logged with context — never silently discarded.
- **No leakage:** never return stack traces, SQL, internal class names, or secrets to clients.

## 2. Error categories → status

| Category | Exception (example) | Status |
|---|---|---|
| Validation | `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 |
| Authentication | `AuthenticationException` | 401 |
| Authorization | `AccessDeniedException`, ownership violation | 403 |
| Not found | `ResourceNotFoundException` (`TaskNotFoundException`, …) | 404 |
| Conflict | `DuplicateResourceException`, optimistic lock | 409 |
| Payload too large | upload size exceeded | 413 |
| Unprocessable | malformed/unrepairable model output | 422 |
| Rate limit / budget | `RateLimitExceededException`, guardrail budget | 429 |
| Tool error | `ToolExecutionException` (surfaced as observation or safe error) | 422/500 per case |
| AI provider error | `LlmProviderException` (timeout, unavailable) | 502/503 (safe message) |
| Persistence | data-access failures | 500 (safe message) |
| Unexpected | anything uncaught | 500 (safe message) |

## 3. Agent/tool error flow

Within an agent run, most errors are **not** HTTP errors — they become **observations** the loop sees (`AGENT_ARCHITECTURE.md` §5, `GUARDRAILS.md`):

- Authorization denied / invalid arguments → observation; agent may recover, refuse, or report; audited.
- Tool timeout/failure → retry within limit (idempotent tools) → else safe failure surfaced.
- Provider failure → graceful degradation; optional fallback only if enabled + privacy-reviewed.

Only when a run cannot proceed does the endpoint return a top-level error (e.g. 422 for unrepairable output, 429 for budget).

## 4. Domain exceptions

Throw specific domain exceptions (`TaskNotFoundException`, `ToolAuthorizationException`, …) mapped centrally — not generic `RuntimeException`. Each carries enough context to log and to build a safe message.

## 5. Logging & correlation

Every handled error logs at the appropriate level (4xx → WARN, 5xx → ERROR) with its `traceId`, `status`, `code`, and `path`, without secrets or full payloads (`OBSERVABILITY.md`, `DATA_PRIVACY.md`). 5xx causes are logged with the full stack trace server-side only — never returned to the client.

- **M1 (IMPLEMENTED):** `traceId` is a per-response UUID generated in the handler; it appears in both the response envelope and the log line, so a client-reported id maps to a server log.
- **M10 (PLANNED):** request-wide correlation-ID propagation via MDC, plus `executionId`/`toolExecutionId` on the agent/tool path.

## 6. Testing

Cover each mapped category: 400 (validation), 401/403 (auth), 404, 409, 422 (malformed model output), 429 (guardrail). Assert responses use the envelope and leak no internals (`TESTING.md`).
