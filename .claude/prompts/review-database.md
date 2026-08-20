# Prompt: Review database

Use to review a schema change, entity, migration, or query.

---

**Under review:** <migration / entity / query>

## Checklist
1. **Migration discipline.** New Flyway file in `db/migration/`; no edits to an applied migration; forward-only, with backfill/rollback considered.
2. **Schema fidelity.** DDL matches `docs/DATABASE.md`; JPA entities match the DDL. `TIMESTAMPTZ` for timestamps.
3. **Ownership & integrity.** FKs present with correct `ON DELETE` behavior; cascade/orphanRemoval consistent in JPA; NOT NULL / unique constraints correct.
4. **Durable vs ephemeral.** Only durable data in PostgreSQL; conversation/session/execution state in Redis (`docs/MEMORY.md`).
5. **Indexes.** FKs and hot-path `WHERE`/`ORDER BY` columns indexed. No unindexed sort on large tables.
6. **Query safety.** Parameterized/JPA only — no string-built SQL. No `findAll()` without pagination. No N+1 (fetch joins / batch).
7. **Transactions.** Multi-write wrapped in one transaction; transactions short; none spanning an LLM/tool call.
8. **Audit data.** Agent/tool executions and audit events persisted per `docs/AUDIT_LOGGING.md` where relevant.

## Output
Findings by severity with concrete fixes; flag any missing index, unbounded query, or role-confusion between Postgres and Redis.
