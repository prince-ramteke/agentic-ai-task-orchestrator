# Rule: API

Always-on constraints for REST endpoints. See `docs/API.md` for the contract.

## Always
- Base path `/api`. JSON in/out. Auth via `Authorization: Bearer <JWT>` except public routes.
- Public routes only: `/api/auth/**`, `/actuator/health`, Swagger. Everything else is authenticated.
- Resources are nouns; use correct HTTP methods and status codes (see `docs/API.md` table).
- Return the standard error envelope `{timestamp, status, error, message, traceId}` for all errors.
- Validate request DTOs; return `400` with field messages on violation.
- Paginate list endpoints (`page`, `size`, `sort`).
- Document every endpoint in SpringDoc/Swagger and keep `docs/API.md` current.
- Support idempotency for unsafe operations that may be retried where it matters (see `docs/API.md`).

## Never
- Never expose entities or internal fields (password hash, secrets, raw model output) in responses.
- Never return raw, unvalidated LLM text as an API result.
- Never add an endpoint without auth unless it's explicitly whitelisted.
- Never break the shape of a published response (e.g. the agent execution response) without versioning/documenting it.

## New endpoint checklist
DTOs + validation → service method (ownership check) → controller → Swagger annotations → tests (happy + 400 + 401/403) → update `docs/API.md`.

## Work that belongs here
Endpoint design, request/response contracts, status codes, validation, pagination, error envelope, versioning, and Swagger/OpenAPI.

## Skills for this area
- **Auto-consult:** `engineering:system-design`. Always read `rules/security` (every endpoint is an attack surface) and `rules/backend`.
- **Verify before done:** `engineering:code-review`.
- **Ignore:** frontend/design and deployment skills. A contract change must update `docs/API.md` and Swagger.
