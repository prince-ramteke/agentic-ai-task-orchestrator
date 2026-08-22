# API Contract
## Agentic AI Task Orchestrator

> Conceptual contract. Endpoints are **planned** (M2+), not implemented. This defines the style every endpoint must follow.

## 1. Conventions

- Base path **`/api/v1`**. JSON in/out. UTF-8.
- Auth via `Authorization: Bearer <JWT>` on all non-public routes.
- Public routes only: `/api/v1/auth/**`, `/api/v1/health`, `/actuator/health`, `/actuator/info`, Swagger.
- Resources are **nouns**, plural (`/tasks`, `/customers`). Sub-resources nest logically.
- Correct HTTP methods: GET (read, safe) · POST (create/action) · PUT (full update) · PATCH (partial) · DELETE (remove).
- Every endpoint documented in SpringDoc/Swagger and here.

## 2. Status codes

| Code | Use |
|---|---|
| 200 | Successful read/update |
| 201 | Resource created (with `Location`) |
| 202 | Accepted (async/long-running, if added) |
| 204 | Success, no body (e.g. delete) |
| 400 | Validation error (field messages) |
| 401 | Missing/invalid authentication |
| 403 | Authenticated but not authorized (RBAC/ownership) |
| 404 | Resource not found (or not owned — avoid leaking existence) |
| 409 | Conflict (duplicate, version) |
| 413 | Payload too large |
| 422 | Semantically invalid (e.g. unrepairable model output) |
| 429 | Rate limited / guardrail budget |
| 500 | Unexpected server error (no internals leaked) |

## 3. Error envelope (all errors) — IMPLEMENTED (M1)

```json
{
  "timestamp": "2026-08-21T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "path": "/api/v1/example",
  "traceId": "f7a7b61f-0622-4dbf-ba17-9131af0d27da",
  "fieldErrors": [ { "field": "title", "message": "must not be blank" } ]
}
```

- `error` is a **stable machine code** (e.g. `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR`) — clients branch on it, not on the human `message`.
- `path` is the request URI.
- `traceId` is a **per-response generated UUID** so a user can quote it in support. Full request-wide correlation-ID propagation across the agent/tool path is PLANNED for M10 (`OBSERVABILITY.md`).
- `fieldErrors` present only for validation failures (omitted otherwise).
- Never includes stack traces or internal detail (`ERROR_HANDLING.md`).

## 4. Validation

Request DTOs are records annotated with Bean Validation; violations produce `400` with `fieldErrors`. Model-generated tool arguments are validated separately inside tools (`TOOL_SYSTEM.md`).

## 5. Pagination, sorting, filtering

List endpoints accept `page` (0-based), `size` (bounded max), `sort` (`field,asc|desc`), and documented filter params. Responses include pagination metadata (page, size, totalElements, totalPages).

## 6. Idempotency & versioning

- Retryable unsafe operations (e.g. agent-initiated creates) support an idempotency key where duplication matters.
- Breaking changes to a published response shape (notably the agent execution response) require versioning and a documented migration — never a silent change.

## 6a. Implemented endpoints — VERIFIED

| Method | Path | Auth | Purpose | Since |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Register a user (always `ROLE_USER`) | M2 |
| POST | `/api/v1/auth/login` | public | Authenticate → JWT access token | M2 |
| GET | `/api/v1/me` | **authenticated** | Current principal (userId, email, roles) | M2 |
| GET | `/api/v1/admin/ping` | **ROLE_ADMIN** | RBAC demonstration/probe | M2 |
| GET | `/api/v1/health` | public | App liveness + name/version/active-profiles | M1 |
| GET | `/actuator/health` · `/actuator/info` | public | Operational health / app metadata | M1 |
| GET | `/v3/api-docs` · `/swagger-ui.html` | public | OpenAPI document + Swagger UI (Bearer-aware) | M1/M2 |

> Deny-by-default: every route not listed as public requires `Authorization: Bearer <JWT>`.
> Only `health` and `info` Actuator endpoints are exposed; all others (env, beans, metrics, heapdump, …) are not exposed — 401 to anonymous callers, 404 to authenticated ones (`SECURITY.md`).

### Auth request/response shapes

**`POST /api/v1/auth/register`** — body `{ "email": "user@example.com", "password": "ExamplePassword123!" }`
- `email`: required, valid email, ≤255 chars. `password`: required, 8–72 chars (BCrypt input limit).
- **201** → `{ "id", "email", "roles": ["ROLE_USER"], "createdAt" }` (never the password hash).
- **409 `EMAIL_ALREADY_EXISTS`** on duplicate (case-insensitive). **400 `VALIDATION_ERROR`** on invalid input.

**`POST /api/v1/auth/login`** — body `{ "email", "password" }`
- **200** → `{ "accessToken": "<JWT>", "tokenType": "Bearer", "expiresIn": 3600 }`.
- **401 `INVALID_CREDENTIALS`** — identical for unknown user, wrong password, or disabled account (no enumeration).

**Protected calls** send `Authorization: Bearer <accessToken>`.
- Missing/invalid/expired/forged token on a protected route → **401 `UNAUTHORIZED`**.
- Authenticated but insufficient role → **403 `FORBIDDEN`**.

Machine error codes in the envelope (`error` field): `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `FORBIDDEN`, `CONFLICT`, `INTERNAL_ERROR`, and (M4, AI layer) `LLM_UNAVAILABLE`, `LLM_TIMEOUT`, `LLM_PROVIDER_ERROR`, `LLM_INVALID_OUTPUT`.

### Task & Customer endpoints (M3) — VERIFIED

All require `Authorization: Bearer <JWT>`. Owner is assigned server-side from the token — a client
`ownerId` in the body is ignored. A USER sees only their own resources; a request for another user's
resource (or a non-existent id) returns **404** (existence-masking, never 403). An ADMIN may
GET/PUT/DELETE any single resource by id, but list endpoints always return only the caller's own.

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/v1/tasks` | 201 + `Location` | body = `TaskResponse` |
| GET | `/api/v1/tasks` | 200 | `PageResponse<TaskSummaryResponse>`; params `page,size,sort,status,priority,dueBefore` |
| GET | `/api/v1/tasks/{id}` | 200 | `TaskResponse` (404 if not owned/absent) |
| PUT | `/api/v1/tasks/{id}` | 200 | **full replacement**; `status` & `priority` required |
| DELETE | `/api/v1/tasks/{id}` | 204 | hard delete |
| POST | `/api/v1/customers` | 201 + `Location` | 409 `CONFLICT` on duplicate `email` for the same owner |
| GET | `/api/v1/customers` | 200 | `PageResponse<CustomerSummaryResponse>`; params `page,size,sort,status,search` |
| GET | `/api/v1/customers/{id}` | 200 | `CustomerResponse` (404 if not owned/absent) |
| PUT | `/api/v1/customers/{id}` | 200 | full replacement; `status` required; 409 on email conflict |
| DELETE | `/api/v1/customers/{id}` | 204 | hard delete |

**Pagination/sort/filter.** `page` (0-based, default 0), `size` (default 20, **max 100** — clamped),
`sort=field,asc|desc` restricted to a whitelist (unknown field → 400): tasks
`{createdAt,updatedAt,dueDate,priority,status,title}`, customers `{createdAt,updatedAt,name,status}`;
default sort `createdAt,desc`. Response envelope: `{ content[], page, size, totalElements, totalPages, first, last }`.
Task filters: `status`, `priority`, `dueBefore` (tasks with `due_date` ≤ the date). Customer filters:
`status`, `search` (case-insensitive substring on name or email). An invalid enum value in a query
param → 400 `VALIDATION_ERROR`.

**Field validation.** Task: `title` required ≤200; `description` ≤2000; `estimatedHours` ≥0, ≤9999.99;
`status`/`priority` valid enum values. Customer: `name` required ≤150; `email` valid & ≤255; `phone`
≤30 (digits/spaces/`+-()`); `status` valid enum. Violations → 400 with `fieldErrors`.

### AI (LLM) endpoints (M4) — VERIFIED

Minimal demonstration of the LLM infrastructure layer. **Authenticated** (deny-by-default). This is
**not** the agent — no tools, planning, or autonomy (that is M6). All responses are application DTOs;
no raw provider object is ever returned.

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/api/v1/ai/generate` | 200 | body `{ "prompt": "…" }` (`@NotBlank`, ≤4000) → `{ content, model, provider }` |
| POST | `/api/v1/ai/classify` | 200 | body `{ "text": "…" }` (`@NotBlank`, ≤4000) → `{ category, priority, summary, model, provider }` |

`classify` returns a **typed, validated** result: `category ∈ {BUG,FEATURE,QUESTION,OTHER}`,
`priority ∈ {LOW,MEDIUM,HIGH}`. The model produces `{category,priority,summary}` via Spring AI's
structured-output converter; the service re-validates it (Bean Validation) and repairs once before
failing. Provider/model metadata is server-supplied, never model output.

**AI error codes** (standard envelope): **503 `LLM_UNAVAILABLE`** (provider down / model missing),
**504 `LLM_TIMEOUT`** (read timeout), **502 `LLM_PROVIDER_ERROR`** (other provider/runtime failure),
**422 `LLM_INVALID_OUTPUT`** (model output failed validation after one repair). Input violations →
**400 `VALIDATION_ERROR`**; unauthenticated → **401 `UNAUTHORIZED`**.

### Tools endpoint (M5) — VERIFIED

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| GET | `/api/v1/tools` | **ROLE_ADMIN** | 200 | Read-only list of registered tool descriptors (metadata only) |

Each entry: `{ name, description, category, version, risk, requiresAuthentication, requiredRoles,
inputType, outputType }` — `inputType`/`outputType` are **simple type names**, never implementation
class names. USER → 403 `FORBIDDEN`; anonymous → 401. There is **no** tool-execution endpoint in M5;
tools are invoked in-process via `ToolExecutor` and will be driven by the agent in M6.

**Tool error codes** (surfaced inside `ToolResult` observations, and via the standard envelope if a
tool exception ever reaches HTTP): `TOOL_NOT_FOUND` (404), `TOOL_INVALID_INPUT` (400),
`TOOL_UNAUTHORIZED` (401), `TOOL_FORBIDDEN` (403), `TOOL_TIMEOUT` (504, reserved for M8),
`TOOL_EXECUTION_FAILED` (500). A domain error (e.g. `NOT_FOUND`) is preserved with its own code.

### Agent endpoint (M6 + M7 memory + M8 guardrails) — VERIFIED

| Method | Path | Auth | Success | Notes |
|---|---|---|---|---|
| POST | `/api/v1/agent/execute` | **authenticated** | 200 | Run one bounded agent execution; optionally continue a conversation |
| POST | `/api/v1/agent/confirmations/{id}` | **authenticated** | 200 | Confirm & execute a pending side-effecting action **exactly once** (no request body) |
| DELETE | `/api/v1/agent/confirmations/{id}` | **authenticated** | 204 | Cancel a pending confirmation (404-masked) |
| DELETE | `/api/v1/agent/conversations/{id}` | **authenticated** | 204 | Delete the caller's conversation memory (404-masked) |

Request: `{ "message": "Show me my high-priority tasks", "conversationId"?: "<uuid>" }` — `message` is
`@NotBlank`, ≤ **4000** chars; `conversationId` is **optional** and, when present, must be a UUID
(non-UUID → `400 VALIDATION_ERROR`). **Absent `conversationId` starts a new conversation.**

Response (200): `{ "executionId", "status", "response", "iterations", "toolCalls", "durationMs",
"failureCode", "conversationId", "memoryStatus" }` where `status ∈ COMPLETED | FAILED | TIMED_OUT |
CANCELLED | LIMIT_REACHED | LOOP_DETECTED | PENDING_CONFIRMATION | BLOCKED` and `failureCode` is
present (non-null) only for non-`COMPLETED` runs.

**M8 guardrails (additive, non-breaking — existing fields unchanged; `NON_NULL`-omitted otherwise):**
- `status = PENDING_CONFIRMATION` — a SIDE_EFFECTING/HIGH_RISK action was proposed and **halted before
  any effect**. The response adds `confirmationId`, `confirmationTool`, `confirmationRiskLevel`,
  `confirmationSummary`, `confirmationExpiresAt`. Confirm it (exact stored action, single-use) via
  `POST /api/v1/agent/confirmations/{id}` — **no request body**; the stored action is what runs, so
  argument mutation is structurally impossible. Confirm response: `{ "confirmationId", "tool",
  "status" (EXECUTED|FAILED), "resultSummary", "errorCode"? }`.
- `status = BLOCKED` — a guardrail denied the action or the per-user tool-call rate limit tripped;
  `failureCode ∈ UNSAFE_ACTION | POLICY_VIOLATION | RATE_LIMITED`.
- **Confirmation errors** (on the confirm endpoint, standard `ApiError` envelope): `404
  CONFIRMATION_NOT_FOUND` (missing/foreign/consumed, masked), `410 CONFIRMATION_EXPIRED`, `409
  CONFIRMATION_MISMATCH` / `CONFIRMATION_ALREADY_USED`, `429 RATE_LIMITED`. Identity comes only from the
  authenticated principal; confirmations are owner-scoped and fingerprint-bound. See ADR-0021…0025,
  `GUARDRAILS.md`.

**M7 memory (additive, non-breaking — existing M6 fields unchanged):**
- `conversationId` — the **server-minted UUID** to continue this conversation on the next call; `null`
  when a new conversation could not be persisted (Redis unavailable). Server-minted only; clients never
  supply their own new id.
- `memoryStatus` — `ACTIVE` (loaded/created and persisted) or `UNAVAILABLE` (Redis down: a new
  conversation ran stateless, or an existing turn could not be persisted best-effort).
- **Ownership & failure:** a missing / expired / non-owned `conversationId` → **404**
  `CONVERSATION_NOT_FOUND` (existence-masked). Redis unreachable while loading an **existing**
  conversation → **503** `MEMORY_UNAVAILABLE` (fail-closed, before any tool runs). `DELETE` returns
  `204` on success, `404` for a missing/foreign conversation. See ADR-0017…0020, `MEMORY.md`.

**Two-tier error model.** A run that actually starts and then terminates in a controlled state
(`FAILED`/`LIMIT_REACHED`/`LOOP_DETECTED`/`TIMED_OUT`/`CANCELLED`) returns **HTTP 200** with a stable
`failureCode` (`AGENT_INVALID_DECISION`, `AGENT_ITERATION_LIMIT`, `AGENT_TOOL_CALL_LIMIT`,
`AGENT_TIMEOUT`, `AGENT_CANCELLED`, `AGENT_LOOP_DETECTED`, `AGENT_LLM_ERROR`, `AGENT_EXECUTION_FAILED`)
— the body carries run metadata (`iterations`, `toolCalls`, `durationMs`) an error envelope cannot.
Only **pre-execution** faults use the standard `ApiError` envelope: request-body validation → `400
VALIDATION_ERROR`; missing/invalid auth → `401`. Identity comes only from the authenticated principal;
the request body carries no `userId`/`role`/`ownerId`. Never returns raw, unvalidated model text — the
`response` is the agent's `FINAL` answer, grounded in actual tool results. See ADR-0013…0016.

## 7. Planned endpoints (design only — not implemented)

> Auth (M2), Task/Customer (M3), AI (M4), Tools (M5) and the Agent execute endpoint (M6) are
> **implemented**. Only the execution-retrieval endpoint below remains design-only (needs durable
> audit — M9).

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/agent/executions/{id}` | USER (owner) / ADMIN | Retrieve a durable execution record + audited steps (**M9**) |

A future `NEEDS_CONFIRMATION` status and a confirm-token resume flow arrive with the M8 confirmation
workflow; M6 does not execute a human-confirmation step.

## 8. New-endpoint checklist

DTOs + validation → service (ownership check) → controller → Swagger → tests (happy + 400 + 401/403, +422 if model output) → update this doc.
