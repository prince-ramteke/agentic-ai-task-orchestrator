# Tool System
## Agentic AI Task Orchestrator

> The contract every future tool must satisfy. No tools are implemented yet (planned M5+).

## 1. What a tool is

A **tool** is an explicitly registered, permission-controlled backend capability the agent may invoke. It is the *only* bridge from the agent to data or effects. Tools are owned and executed by our backend; the model only proposes which to call and with what arguments.

## 2. Tool contract (required for every tool)

| Element | Requirement |
|---|---|
| **Name** | Unique, stable, descriptive (e.g. `searchTasks`). Changing it is a contract change. |
| **Description** | Clear, model-facing purpose + when to use it. Drives selection quality. |
| **Input schema** | Typed parameters with types, constraints, required/optional. |
| **Output schema** | Typed, structured result. Never free-form text passed through as an API result. |
| **Authorization requirement** | Which role and which ownership check must pass before execution. |
| **Side-effect classification** | Read-only · Deterministic · Side-effecting · High-risk (see §4). |
| **Validation rules** | Every argument validated against the schema before use. |
| **Timeout** | Max execution time; enforced by the orchestrator/guardrails. |
| **Retry policy** | Whether/how it may be retried (idempotent tools only). |
| **Audit requirements** | What is recorded on invocation and completion (`AUDIT_LOGGING.md`). |
| **Error model** | Typed, safe errors returned as observations (see `ERROR_HANDLING.md`). |

## 3. Execution gates (in order, non-negotiable)

```
proposed tool + args (from LLM)
    → registry: is this tool permitted in context?      (least privilege)
    → authorization: may this user act on this target?   (server-side, before effect)
    → validation: do the arguments satisfy the schema?
    → confirmation: if dangerous, is confirmation given?
    → execute: deterministic domain logic, within timeout
    → audit: record invocation, decision, result, side effects
    → observation: return typed result to the loop
```

If any gate fails, the tool does not execute; the failure becomes an audited observation.

## 4. Risk classification (security strictness increases with risk)

| Class | Meaning | Examples (planned) | Required treatment |
|---|---|---|---|
| **Read-only** | No state change | `searchTasks`, `getTask`, `searchCustomer` | Auth + ownership filter; safe to retry. |
| **Deterministic** | Pure computation, no I/O side effects | `calculate` | Validate inputs; no external effect. |
| **Side-effecting** | Creates/updates state | `createTask`, `updateTask`, (future) `sendEmail` | Auth + ownership + validation + audit; idempotency where retryable. |
| **High-risk** | Irreversible / destructive / external irreversible | `deleteTask`, `deleteCustomer`, external irreversible ops | All of the above **plus mandatory confirmation** before execution; strict audit. |

## 5. Argument trust

Arguments are model-generated and therefore **untrusted**. Every argument — especially resource IDs — is validated and re-checked against the authenticated user's permissions. A tool never trusts an ID or an authorization claim supplied through the model.

## 6. Registering a tool (checklist)

1. Define typed input/output records + validation.
2. Implement deterministic execution over a domain service (no logic in the tool wrapper beyond orchestration).
3. Add authorization + ownership check as the first executable step.
4. Classify the side effect; set timeout + retry policy; gate high-risk with confirmation.
5. Add audit hooks (invocation + result).
6. Register with a unique name + model-facing description in the registry, scoped to the contexts where it's allowed.
7. Unit-test: happy path, argument validation failure, authorization refusal, (for side-effecting) audit produced, (for high-risk) confirmation required.
8. Add representative evaluation cases (`EVALUATION.md`).
9. Document the tool here.

## 7. Future tools (planned, not implemented)

`searchTasks` · `getTask` · `createTask` · `updateTask` · `deleteTask` · `searchCustomer` · `getCustomer` · `calculate` · (later) `sendEmail`, `calendar`, `knowledgeSearch`, `weather`. Each will be added only with the full contract above.

## 8. Anti-patterns (never do)

- A tool that runs arbitrary model-provided SQL, code, or shell.
- A tool that skips authorization because "the agent already decided".
- A tool returning raw model text as its output.
- A high-risk tool that executes without confirmation.
- A tool whose arguments are used before validation.
