# Product Requirements Document (PRD)
## Agentic AI Task Orchestrator

> Status: Milestone 0 draft. Describes the intended product. No functionality is implemented yet.

---

## 1. Problem

Knowledge workers express intent in natural language ("close out my overdue work and schedule the follow-up"), but software forces them to translate that intent into a precise sequence of clicks and API calls. General-purpose chatbots can *talk* about the work but cannot safely *do* it against real business data — they lack authorization, validation, auditability, and bounded execution.

This project builds the missing safe execution layer: an agent that turns an objective into a **bounded, authorized, audited sequence of backend tool calls** against the user's own data.

## 2. Target users

- **End user (role: USER).** Owns tasks and customer records; issues natural-language objectives and receives an execution summary grounded in what actually happened.
- **Administrator (role: ADMIN).** Manages users and inspects agent/tool execution history and audit logs across the system.
- **(Indirectly) the engineering/hiring audience.** The system is also a portfolio artifact demonstrating production agentic engineering (see `PROJECT_CHARTER.md`).

## 3. Core use cases (planned)

| # | Objective (natural language) | Expected agent behavior |
|---|---|---|
| U1 | "Show my overdue tasks." | Select read-only `searchTasks` with a filter; return results. No writes. |
| U2 | "Total the estimated hours on my overdue tasks." | `searchTasks` → deterministic `calculate` over the results; return the sum. |
| U3 | "Find my overdue tasks, total their hours, and create a high-priority follow-up." | Multi-step: `searchTasks` → `calculate` → side-effecting `createTask` (authorized) → summary. |
| U4 | "Delete task #42." | High-risk `deleteTask` → **requires explicit confirmation** before executing; authorization checked. |
| U5 | "What's the weather for my site visit?" (tool not registered / not permitted) | Agent refuses or explains it lacks that capability — does not fabricate. |
| U6 | Objective referencing another user's data | Authorization denies; agent reports it cannot access that resource. |

## 4. Core functional requirements (planned)

- FR1 — Users register and authenticate (JWT); all non-public endpoints require auth.
- FR2 — Users perform CRUD on their own tasks and read their customers via REST.
- FR3 — A `POST /api/agent/chat` endpoint accepts an objective and runs the agent.
- FR4 — The agent selects among **registered tools only**, validates arguments, checks authorization, and executes within guardrail bounds.
- FR5 — Side-effecting/high-risk tools require confirmation before execution.
- FR6 — Every agent run produces a durable, retrievable execution record (`GET /api/agent/executions/{id}`) with the tool steps taken.
- FR7 — Admins can inspect executions and audit logs.

## 5. Future functionality (out of scope for v1, on the roadmap)

Streaming responses; scheduled/long-running workflows; additional tools (email, calendar, knowledge search); multi-tenant isolation; event-driven integration (Kafka); external model providers; richer frontend. See `ROADMAP.md`.

## 6. Out of scope (not planned)

- The agent controlling arbitrary infrastructure or executing model-generated code.
- The LLM as a security or correctness authority.
- Autonomous irreversible actions without confirmation.
- Handling regulated data categories without an explicit data-privacy review.

## 7. Success criteria

**Product:** a user can issue U1–U6-class objectives and get correct, authorized, auditable outcomes, with unauthorized/dangerous actions safely refused or gated.

**Engineering (measurable, once implemented — targets, not yet achieved):**
- Agent evaluation suite passing at an agreed threshold on tool-selection and argument accuracy (`EVALUATION.md`).
- 100% of side-effecting tools enforce authorization before execution (verified by tests).
- 0 unauthorized cross-user actions succeed in the evaluation/security suite.
- Test coverage meets the gate in `TESTING.md`.
- One-command `docker-compose up` brings the full stack up from a clean clone.
- Latency percentiles (p50/p95/p99) for the agent endpoint measured and reported (`PERFORMANCE.md`) — reported only after real measurement.

## 8. Assumptions & constraints

- Local development uses Ollama; no user data leaves the machine unless cloud fallback is explicitly enabled and privacy-reviewed (`DATA_PRIVACY.md`).
- Single-tenant for v1; multi-tenant is a future milestone.
- The model may be imperfect; the system's correctness must not depend on the model being correct — it depends on validation, authorization, and bounds.
