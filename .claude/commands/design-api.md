# Command: /design-api

Design an API surface before implementing it.

**Usage:** `/design-api <resource / capability>`

## Steps
1. **Load** `docs/API.md`, `.claude/rules/api.md`, `.claude/rules/security.md`. Invoke `engineering:system-design`.
2. **Model the resource.** Nouns, relationships, and the operations users actually need. Check `docs/DATABASE.md` and existing endpoints to avoid duplication.
3. **Define contracts.** For each endpoint: method, path, request DTO (with validation), response DTO, status codes, error cases, pagination/sort/filter, and auth/ownership requirement.
4. **Consistency.** Match the standard error envelope, naming, versioning, and idempotency conventions in `docs/API.md`. Do not break existing published response shapes.
5. **Security review.** Which role can call it? What ownership check applies? Is anything sensitive exposed?
6. **Document the proposed contract** in `docs/API.md` (marked as design/planned until implemented) and sketch the Swagger shape.
7. **Present** the design for confirmation before implementation. Then hand off to `/add-endpoint` or `/new-feature`.

Design only — this command produces a contract, not code.
