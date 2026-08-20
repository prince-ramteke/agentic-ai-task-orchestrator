# Guardrails
## Agentic AI Task Orchestrator

> Conceptual. Guardrails are planned (M8). None are implemented yet. Bounds are enforced by code, never by trusting the model.

## 1. Why guardrails exist

The model is an untrusted planner. Guardrails are the deterministic bounds that make an agent run safe, terminating, and predictable regardless of what the model proposes.

## 2. The guardrail set

| Guardrail | Rule | Default (env) | On breach |
|---|---|---|---|
| **Max tool calls** | Cap the number of tool executions per run | `AGENT_MAX_TOOL_CALLS=10` | Stop; return partial summary marked incomplete; audit. |
| **Execution timeout** | Cap wall-clock time per run | `AGENT_TIMEOUT_SECONDS=60` | Stop at next safe point; audit. |
| **Retry limit** | Cap retries per failing step | `AGENT_MAX_RETRIES=2` | Fail the step gracefully; audit. |
| **Loop detection** | Detect repeated identical/duplicate tool calls or non-progress | — | Halt with a clear reason; audit. |
| **Argument validation** | Validate every model argument before use | always on | Reject; return as observation; audit. |
| **Permission checks** | Authorize before any effect | always on | Deny; return as observation; audit. Never bypass. |
| **Confirmation** | Dangerous/irreversible ops require explicit confirmation | always on for high-risk | Do not execute; surface confirmation request. |
| **Input validation** | Validate the incoming objective/request | always on | 400; do not start a run on invalid input. |
| **Output validation** | Validate/parse model output into typed objects | always on | Repair/retry within limit, else 422-class failure. |
| **Model-failure fallback** | Handle provider errors/timeouts | always on | Safe error; optional fallback only if enabled + privacy-reviewed. |
| **Rate limiting** | Cap agent invocations per user/time | configurable | 429; audit. |
| **Cancellation** | Allow a run to be cancelled | always on | Stop at next safe point; record cancellation. |

## 3. Expected failure behavior

Failures are **explicit, bounded, and observable** — never silent:

- A blocked action (authorization, validation, confirmation-pending) becomes an **observation** the agent and audit see; the agent may recover, refuse, or report.
- Exhausting a budget ends the run with a **partial result clearly labeled incomplete**, plus an audited reason.
- Provider/model failures degrade gracefully to a safe error message; they never crash the request or leak internals.

## 4. Interaction with the orchestrator

Guardrails wrap the decision loop (`AGENT_ARCHITECTURE.md` §3). Before each step: check remaining budget, timeout, and loop state. Around each tool: enforce validation, authorization, confirmation, timeout, and retry. After each step: decrement budget, record audit, update execution state in Redis.

## 5. Confirmation flow (planned)

1. Agent proposes a high-risk tool.
2. Guardrail intercepts; instead of executing, the run returns a **confirmation request** describing the exact action and target.
3. Execution resumes only on explicit user confirmation, re-validating authorization at execution time (not just at proposal time).

## 6. Testing

Each guardrail has a test proving it trips (`TESTING.md`): budget exhaustion, timeout, retry cap, loop detection, validation/authorization refusal, confirmation-required, rate limit, and cancellation. There must be **no code path that allows unbounded execution**.
