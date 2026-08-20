# Database Design
## Agentic AI Task Orchestrator

> Conceptual model. No schema, entities, or migrations exist yet (planned M3+). Durable data only — ephemeral state lives in Redis (`MEMORY.md`).

> **Milestone 1 status:** persistence (Spring Data JPA, the PostgreSQL driver, and Flyway) is **deliberately deferred to Milestone 3** — see **ADR-0002**. The M1 backend boots with **zero external infrastructure**; the planned datasource wiring is documented as a commented, inactive block in `backend/src/main/resources/application.yml`, and `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` in `.env.example` remain PLANNED (unused until M3).

## 1. Entities (conceptual)

| Entity | Purpose | Owner |
|---|---|---|
| `users` | Accounts: credentials (BCrypt hash), status | self |
| `roles` | RBAC roles (USER, ADMIN); user↔role mapping | system |
| `tasks` | User tasks: title, description, status, priority, estimated hours, due date | user |
| `customers` | User's customer records: name, contact, metadata | user |
| `agent_executions` | One row per agent run: objective, status, timestamps, user | user |
| `tool_executions` | One row per tool call within a run: tool name, args (redacted), result summary, outcome | via execution |
| `audit_events` | Durable audit trail (`AUDIT_LOGGING.md`) | system |
| `conversations` / `messages` | Only if durable conversation history is required; otherwise conversation context stays in Redis | user |

## 2. Relationships

```mermaid
erDiagram
    users ||--o{ tasks : owns
    users ||--o{ customers : owns
    users ||--o{ agent_executions : initiates
    agent_executions ||--o{ tool_executions : contains
    users ||--o{ audit_events : subject_of
    agent_executions ||--o{ audit_events : produces
    users }o--o{ roles : has
```

- Ownership FKs (`user_id`) on `tasks`, `customers`, `agent_executions`.
- `tool_executions.execution_id` → `agent_executions.id`.
- `audit_events` reference `user_id`, `execution_id`, and carry `correlation_id`.

## 3. Conventions

- `snake_case` table/column names; surrogate primary keys (UUID or bigserial — chosen via ADR at M3).
- Timestamps as `TIMESTAMPTZ` → `Instant`/`OffsetDateTime`.
- Ownership FKs `ON DELETE CASCADE` at the DB level; cascade/orphanRemoval mirrored in JPA where appropriate.
- Enumerations (task status/priority, execution status, tool outcome) stored as constrained strings or DB enums, mirrored by Java enums.

## 4. Indexing strategy

- Index every FK (`user_id`, `execution_id`).
- Index hot-path filters/sorts: `tasks(user_id, status)`, `tasks(user_id, due_date)`, `agent_executions(user_id, created_at)`, `audit_events(execution_id)`, `audit_events(user_id, created_at)`.
- Every list query is paginated; no unindexed sort on a large table.

## 5. Transactions

- Multi-write operations run in a single `@Transactional` unit; reads use `readOnly = true`.
- **No transaction spans an LLM or external tool call.** Load data, commit, then call the model; persist results in a new transaction.

## 6. Migrations (Flyway)

- All schema changes via versioned migrations in `db/migration/` (e.g. `V1__init.sql`). Never edit an applied migration; add a new one.
- Migrations are forward-only and reviewed; backfill/rollback considerations noted in the PR.
- JPA entities are kept in sync with the DDL and this doc; DTOs at the API boundary (never expose entities).

## 7. Durable vs ephemeral (must stay separate)

Durable → Postgres (this doc). Ephemeral conversation/session/execution *working* state and caches → Redis with TTLs (`MEMORY.md`). The **outcome** of an execution is persisted here even though its in-flight working state lived in Redis.

## 8. Audit as first-class data

`agent_executions`, `tool_executions`, and `audit_events` are core domain tables, not an afterthought — they make the agent auditable and evaluable (`AUDIT_LOGGING.md`, `EVALUATION.md`).
