# Tool System
## Agentic AI Task Orchestrator

> **Milestone 5 status: IMPLEMENTED.** The deterministic tool framework now exists in
> `com.prince.agentic.tool`: `Tool<I,O>`, `ToolDescriptor`, `ToolRiskLevel`, `ToolExecutionContext`,
> `ToolResult<O>`, a fail-fast immutable `ToolRegistry`, and a `ToolExecutor` enforcing the ordered
> gates. Six tools are registered (see §7). The framework is **independent of Spring AI / `ai.*`**
> (enforced by a test); the **agent that drives these tools is M6 PLANNED** — nothing here selects or
> executes a tool autonomously yet. See ADR-0011/0012 and
> `docs/superpowers/specs/2026-08-21-m5-tool-system-design.md`.

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

> **Future contract with the M3 domain (must hold):** the deterministic "execute" step calls the
> existing domain services — `TaskService` / `CustomerService` — passing the authenticated
> `AuthenticatedUser`. A tool MUST NOT touch `EntityManager`, `JdbcTemplate`, or a repository
> directly, because those bypass the ownership/authorization and business rules the services
> enforce. The domain service is the business boundary; the tool is a thin, audited adapter over it.
> Target shape: `AI tool → TaskService/CustomerService → AuthorizationService → Repository → PostgreSQL`.

## 4. Risk classification (security strictness increases with risk)

Enum `ToolRiskLevel` (implemented M5):

| Class | Meaning | Examples | Required treatment |
|---|---|---|---|
| **`READ_ONLY`** | No state change | `task.get`, `task.search`, `customer.get`, `customer.search` | Auth + ownership filter (in the service); safe to retry. |
| **`DETERMINISTIC`** | Pure computation, no I/O side effects | `math.calculate` | Validate inputs; no external effect. |
| **`SIDE_EFFECTING`** | Creates/updates state | `task.create`, (future) `task.update` | Auth + ownership + validation + (M9) audit; idempotency where retryable. |
| **`HIGH_RISK`** | Irreversible / destructive / external irreversible | (future) `task.delete`, `customer.delete` | All of the above **plus mandatory confirmation** (M8) before execution; strict audit. |

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

## 7. Registered tools (M5 IMPLEMENTED) + future

**Registered now (least privilege — dot-namespaced names are the stable external identity):**

| Name | Risk | Input | Output | Wraps |
|---|---|---|---|---|
| `task.get` | READ_ONLY | `{taskId}` | `TaskResponse` | `TaskService.get` |
| `task.search` | READ_ONLY | `{status?,priority?,dueBefore?,page?,size?}` | `PageResponse<TaskSummaryResponse>` | `TaskService.list` |
| `task.create` | SIDE_EFFECTING | `TaskCreateRequest` (no ownerId) | `TaskResponse` | `TaskService.create` |
| `customer.get` | READ_ONLY | `{customerId}` | `CustomerResponse` | `CustomerService.get` |
| `customer.search` | READ_ONLY | `{status?,search?,page?,size?}` | `PageResponse<CustomerSummaryResponse>` | `CustomerService.list` |
| `math.calculate` | DETERMINISTIC | `{expression}` | `CalculationResult` | safe `ExpressionEvaluator` |

Shared tools declare roles `{ROLE_USER, ROLE_ADMIN}` (any-of); resource ownership is enforced by the
wrapped service. Search sort is not model-controllable in M5. `math.calculate` supports only
`+ - * / ()`, decimals, and unary minus (no `eval`/`ScriptEngine`).

**Deliberately NOT registered in M5** (least privilege): `task.update`, `task.delete`,
`customer.create`, `customer.update`, `customer.delete`. Destructive/high-risk tools arrive with M8
confirmation/guardrails. **Later:** `sendEmail`, `calendar`, `knowledgeSearch`, `weather`. Each is
added only with the full contract above.

## 7a. Execution pipeline & envelope (M5 IMPLEMENTED)

`ToolExecutor.execute(name, Map<String,Object> rawArguments, ToolExecutionContext)` runs the ordered
gates and returns a `ToolResult<O>` `{toolName, success, data, error{code,message}, durationMs}`:

```
resolve (registry) → authenticate (requiresAuthentication) → authorize (role, any-of)
  → bind rawArguments → inputType (unknown properties rejected) → validate (Bean Validation)
  → execute (domain service enforces ownership) → wrap ToolResult
```

Identity comes only from the context principal (built by the backend, never from arguments). Failures
become `ToolResult` failures with stable codes: `TOOL_NOT_FOUND` (404), `TOOL_INVALID_INPUT` (400),
`TOOL_UNAUTHORIZED` (401), `TOOL_FORBIDDEN` (403), `TOOL_TIMEOUT` (504, reserved for M8),
`TOOL_EXECUTION_FAILED` (500); a domain `ApiException` (e.g. `NOT_FOUND`) is surfaced with its own
code. `TOOL_REGISTRATION_ERROR` fails application boot. Timeout is **metadata + measured duration** in
M5; hard cancellation is M8. An ADMIN-only, read-only `GET /api/v1/tools` returns descriptor metadata.

## 8. Anti-patterns (never do)

- A tool that runs arbitrary model-provided SQL, code, or shell.
- A tool that skips authorization because "the agent already decided".
- A tool returning raw model text as its output.
- A high-risk tool that executes without confirmation.
- A tool whose arguments are used before validation.
