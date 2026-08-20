# Rule: Database

Always-on constraints for persistence. See `docs/DATABASE.md`.

## Always
- Change schema only via a new **Flyway** migration in `db/migration/`. Never edit an applied migration.
- Keep JPA entities in sync with the DDL and the docs.
- Use `snake_case` table/column names; singular-or-consistent naming per `docs/DATABASE.md`.
- Use `TIMESTAMPTZ` → `Instant`/`OffsetDateTime`.
- Enforce ownership foreign keys with `ON DELETE CASCADE` at the DB level and cascade/orphanRemoval in JPA where appropriate.
- Index foreign keys and every column used in a `WHERE`/`ORDER BY` on a hot path.
- Paginate all list queries (`page`, `size`, `sort`). Cap result sizes.
- Wrap multi-write operations in a single transaction; keep transactions short.
- Persist agent executions, tool executions, and audit events as durable records (they are first-class domain data — see `docs/DATABASE.md`, `docs/AUDIT_LOGGING.md`).

## Never
- Never use `findAll()` without pagination.
- Never build SQL by string concatenation — parameterized queries / JPA only.
- Never hold a transaction open across an LLM or external tool call.
- Never store ephemeral conversation/session state in PostgreSQL when it belongs in Redis (see `docs/MEMORY.md`), or durable data in Redis.
- Never expose raw entities or internal columns (password hash, secrets) through the API.

## Work that belongs here
Schema/DDL, JPA entities and relations, Flyway migrations, indexes, query design, transactional boundaries, and audit persistence.

## Skills for this area
- **Auto-consult:** `engineering:system-design` (data modeling). Read `rules/backend` alongside — entities and repositories move together. Use `engineering:architecture` only for a storage-choice ADR.
- **Verify before done:** `engineering:code-review` (N+1, unbounded fetches, missing indexes).
- **Ignore:** frontend/design and deployment skills.
