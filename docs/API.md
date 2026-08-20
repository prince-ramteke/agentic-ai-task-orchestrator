# API Contract
## Agentic AI Task Orchestrator

> Conceptual contract. Endpoints are **planned** (M2+), not implemented. This defines the style every endpoint must follow.

## 1. Conventions

- Base path **`/api`**. JSON in/out. UTF-8.
- Auth via `Authorization: Bearer <JWT>` on all non-public routes.
- Public routes only: `/api/auth/**`, `/actuator/health`, Swagger.
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

Machine error codes in the envelope (`error` field): `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `EMAIL_ALREADY_EXISTS`, `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `FORBIDDEN`, `INTERNAL_ERROR`.

## 7. Planned endpoints (design only — not implemented)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register a user |
| POST | `/api/auth/login` | public | Obtain a JWT |
| GET | `/api/tasks` | USER | List own tasks (paginated, filterable) |
| GET | `/api/tasks/{id}` | USER (owner) | Get a task |
| POST | `/api/tasks` | USER | Create a task |
| PUT | `/api/tasks/{id}` | USER (owner) | Update a task |
| DELETE | `/api/tasks/{id}` | USER (owner) | Delete a task |
| GET | `/api/customers` | USER | List own customers |
| GET | `/api/customers/{id}` | USER (owner) | Get a customer |
| POST | `/api/agent/chat` | USER | Submit an objective; run the agent |
| GET | `/api/agent/executions/{id}` | USER (owner) / ADMIN | Retrieve an execution record + audited steps |

### `POST /api/agent/chat` (shape sketch — subject to change, will be versioned)

Request: `{ "objective": "string", "conversationId": "optional", "confirmToken": "optional (for resuming a confirmed dangerous op)" }`

Response (conceptual): `{ "executionId", "status": "COMPLETED|NEEDS_CONFIRMATION|FAILED|INCOMPLETE", "summary", "steps": [ { "tool", "arguments(redacted)", "result", "outcome" } ], "confirmationRequest": { ... } }`

Never returns raw, unvalidated model text. `NEEDS_CONFIRMATION` carries the exact action to confirm.

## 8. New-endpoint checklist

DTOs + validation → service (ownership check) → controller → Swagger → tests (happy + 400 + 401/403, +422 if model output) → update this doc.
