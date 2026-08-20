# Command: /design-database

Design or evolve the schema before writing a migration.

**Usage:** `/design-database <entity / change>`

## Steps
1. **Load** `docs/DATABASE.md`, `.claude/rules/database.md`, `.claude/rules/backend.md`. Invoke `engineering:system-design`.
2. **Model entities and relations.** Columns, types (`TIMESTAMPTZ`, etc.), nullability, ownership FKs, cascade rules. Check the durable-vs-ephemeral split (`docs/MEMORY.md`) — durable data only.
3. **Indexes.** Plan indexes for FKs and hot-path `WHERE`/`ORDER BY` columns. Plan pagination.
4. **Audit & agent data.** If touching agent/tool executions or audit events, align with `docs/AUDIT_LOGGING.md`.
5. **Migration plan.** A new Flyway migration in `db/migration/` (never edit an applied one). Note backfill/rollback considerations.
6. **Entities.** Map to JPA entities kept in sync with the DDL; DTOs at the boundary.
7. **Consider an ADR** if this is a significant storage decision.
8. **Document** the change in `docs/DATABASE.md`. Present the design for confirmation before implementation.

Design + migration plan only. Implementation follows via `/new-feature`.
