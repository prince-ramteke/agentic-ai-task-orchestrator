# Guardrails
## Agentic AI Task Orchestrator

> Conceptual. Guardrails are planned (M8). None are implemented yet. Bounds are enforced by code, never by trusting the model.

> **Milestone 5 note (hooks in place, enforcement deferred):** the M5 tool framework provides the
> metadata M8 will enforce: every `ToolDescriptor` carries a `ToolRiskLevel`
> (`READ_ONLY/DETERMINISTIC/SIDE_EFFECTING/HIGH_RISK`) and a `timeout`, and the `ToolExecutor` measures
> per-call `durationMs`. M5 **does not** implement confirmation, hard timeout/cancellation, loop
> detection, or rate limiting — destructive tools (`task.delete`, `customer.delete`) are simply **not
> registered** yet (least privilege). M8 adds the enforcement layer on top of this classification.

> **Milestone 6 note (cooperative bounds IMPLEMENTED; hard enforcement still M8):** the M6
> `AgentOrchestrator` implements the **cooperative, in-loop** forms of the bounds below — iteration
> budget, tool-call budget, a single shared wall-clock deadline (computed once), a cooperative
> `CancellationToken`, and fingerprint-based loop detection — all checked **between** steps so no
> unbounded execution path exists. M6 does **not** hard-interrupt an in-flight LLM/tool call, and does
> **not** implement the human-confirmation workflow or rate limiting. **M8 adds the hard enforcement**:
> per-tool hard timeout/interruption, confirmation for side-effecting/irreversible tools, rate limiting,
> and retry hardening. See ADR-0014.

## 1. Why guardrails exist

The model is an untrusted planner. Guardrails are the deterministic bounds that make an agent run safe, terminating, and predictable regardless of what the model proposes.

## 2. The guardrail set

Legend: **[M6]** cooperative, checked between steps (implemented now); **[M8]** hard enforcement (planned).

| Guardrail | Rule | Default (env) | On breach |
|---|---|---|---|
| **Max iterations** [M6] | Cap the number of LLM decision steps per run | `AGENT_MAX_ITERATIONS=8` | Stop; 200 + `AGENT_ITERATION_LIMIT`; metric `agent.limit.reached`. |
| **Max tool calls** [M6] | Cap the number of tool executions per run | `AGENT_MAX_TOOL_CALLS=10` | Stop; 200 + `AGENT_TOOL_CALL_LIMIT`; metric `agent.limit.reached`. |
| **Execution timeout** [M6 cooperative / M8 hard] | Cap wall-clock time per run (one deadline, computed once) | `AGENT_TIMEOUT_SECONDS=60` | M6: stop at next step, 200 + `AGENT_TIMEOUT`. M8: hard interrupt. |
| **Loop detection** [M6] | Detect repeated identical tool calls (tool + canonical args, threshold) | `AGENT_LOOP_THRESHOLD=2` | Halt; 200 + `AGENT_LOOP_DETECTED`; metric `agent.loop.detected`. |
| **Retry limit** [M8] | Cap retries per failing step (M6 does one bounded *decision* repair only; side-effect tools never auto-retried) | `AGENT_MAX_RETRIES=2` *(planned)* | Fail the step gracefully; audit. |
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

## Milestone 7 — Memory boundary note

M7 adds Redis conversation memory but **not** guardrails. Memory content is untrusted context confined
to a delimited prompt slot (never a replacement system prompt), and conversation size is bounded, but
**hard** guardrail enforcement, human confirmation for dangerous operations, hard timeout/interruption,
and rate limiting remain **M8 (PLANNED)**. Prompt-injection via stored memory is bounded here and is an
explicit M8 concern.
