# Agent Architecture
## Agentic AI Task Orchestrator

> Conceptual. The agent itself is not implemented (planned M6–M9). **As of M5**, the two deterministic
> *foundations* this design sits on exist: the **LLM layer** (M4 — `LlmClient`/`AiService`, Ollama
> behind an interface) and the **tool framework** (M5 — `com.prince.agentic.tool`: `Tool<I,O>`,
> `ToolRegistry`, `ToolExecutor`, `ToolExecutionContext`, six registered tools wrapping the M3 domain
> services). There is still **no** orchestrator, tool *selection*, ReAct loop, or Spring AI
> tool-calling — the model cannot invoke anything yet. M6 adds the agent that calls `LlmClient` to
> decide a step and drives the M5 `ToolExecutor`.
>
> **Foundational invariant (must hold through M6+):** the agent may never reach a repository,
> `EntityManager`, arbitrary method, or code. Its only path to effects is:
> `LLM → approved tool name → ToolRegistry → validated input → backend-built ToolExecutionContext →
> role + resource authorization → domain service → result`. Identity comes from the authenticated
> principal, never from model-supplied arguments (see `TOOL_SYSTEM.md`, `SECURITY.md`, ADR-0012).

## 1. What "agent" means here

An **agent** is the backend orchestration loop that turns a user objective into a bounded sequence of **registered tool** executions, using the LLM only to *decide the next step*. It is not a chatbot and not an autonomous process with free rein. The agent is code we control; the model is one (untrusted) input to that code.

## 2. Division of responsibility

| Concern | Owner | Notes |
|---|---|---|
| Understand the objective; propose the next tool + arguments | **LLM** | A *suggestion*, never a command. |
| Decide whether a tool is allowed in context | **Orchestrator / registry** | Least privilege. |
| Authorize the action against the user | **Tool** | Server-side, before any effect. |
| Validate arguments | **Tool** | Against a typed schema. |
| Execute deterministic business logic | **Domain service (via tool)** | The model never runs logic. |
| Enforce bounds, confirmation, loop detection | **Guardrails** | See `GUARDRAILS.md`. |
| Persist state | **Redis (ephemeral) / Postgres (durable)** | See `MEMORY.md`. |
| Record what happened | **Audit** | See `AUDIT_LOGGING.md`. |
| Produce the final response | **Orchestrator** | Grounded in actual tool results. |

**Principle:** the model proposes; the backend disposes.

> **Domain boundary (M3, must not regress):** "Execute deterministic business logic" means calling
> the M3 domain services — `TaskService` / `CustomerService` — which already enforce ownership via
> `AuthorizationService` and validate input. When tools arrive (M5), they call these services with
> the authenticated `AuthenticatedUser`; they never reach `EntityManager`/`JdbcTemplate`/repositories
> directly. This keeps the agent strictly weaker than or equal to the user's own permissions.

## 3. Execution lifecycle

```
User Request
    ↓
Authentication              (JWT; who is the user?)
    ↓
Agent Execution starts      (new execution ID; guardrail budget allocated)
    ↓
Context Loading             (conversation/session state from Redis; permitted tool set)
    ↓
LLM Decision                (given objective + observations, propose next step)
    ↓
Tool Selection              (map proposal to a registered tool, or "final answer")
    ↓
Authorization               (tool checks the user's permission on the target)
    ↓
Tool Validation             (validate arguments against schema)
    ↓
Tool Execution              (deterministic domain logic; timeout applies)
    ↓
Observation                 (structured result fed back, delimited as untrusted)
    ↓
Next Decision               (loop — subject to max calls / timeout / loop detection)
    ↓
Completion / Failure        (final answer, or graceful failure within bounds)
    ↓
Audit                       (decisions, selections, executions, side effects recorded)
    ↓
Response                    (execution summary + retrievable execution record)
```

Between "LLM Decision" and "Tool Execution" every gate must pass. A failure at authorization or validation does **not** end the run silently — it becomes an observation the agent (and audit) sees, and the agent may recover, refuse, or report.

## 4. State

- **Execution state** (current step, tool-call count, budget remaining, pending confirmation) — Redis, keyed by execution ID, with a TTL.
- **Conversation/session context** — Redis, short-lived.
- **Durable execution record** (objective, ordered steps, outcomes) — Postgres, for audit and `GET /api/agent/executions/{id}`.

## 5. Failure states & handling

| State | Handling |
|---|---|
| Malformed model output | Parse → validate → repair/retry within retry limit → else fail gracefully (422-class). |
| Tool authorization denied | Observation returned; agent refuses/reports; audited. Never bypassed. |
| Invalid tool arguments | Observation returned; agent may correct once (bounded); else fail. |
| Tool execution error/timeout | Retry within limit (idempotent tools only); else surface a safe error and stop. |
| Loop / repeated identical calls | Loop detection halts the run with a clear reason. |
| Budget exhausted (max calls/timeout) | Stop; return partial summary labeled as incomplete; audited. |
| Cancellation requested | Stop at the next safe point; record cancellation. |

## 6. Confirmation for dangerous operations

Side-effecting + irreversible tools (delete, external send, critical modification) are **not executed inline**. The agent surfaces a confirmation request; execution proceeds only after explicit user confirmation. See `GUARDRAILS.md`.

## 7. Loop prevention & bounds

Every run carries a budget: max tool calls, wall-clock timeout, retry limit, and duplicate-call/loop detection. Bounds are configurable via env (`AGENT_MAX_TOOL_CALLS`, `AGENT_TIMEOUT_SECONDS`, `AGENT_MAX_RETRIES`) and enforced by guardrails, not by trusting the model to stop.

## 8. Why this design

It isolates the untrusted part (model reasoning) behind deterministic, testable, authorized, audited gates — so correctness and security never depend on the model behaving. It is also evaluable: because tools and decisions are explicit, agent behavior can be scored against a dataset (`EVALUATION.md`).
