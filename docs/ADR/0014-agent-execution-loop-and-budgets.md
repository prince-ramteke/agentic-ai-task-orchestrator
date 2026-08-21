# ADR-0014: Agent Execution Loop and Cooperative Budgets

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince + Claude

## Context

An agent loop that trusts the model to stop is unsafe. The run must terminate and stay bounded
regardless of what the model proposes. Earlier planning (`ROADMAP.md`, `GUARDRAILS.md`) tentatively
placed loop detection, execution timeout, and the tool-call cap in Milestone 8. But a terminating,
bounded loop is a **correctness** requirement of M6 itself — no unbounded execution path may ship in
the milestone that first lets the model cause effects. This forces an explicit split between what M6
must own now and what M8 hardens later.

## Decision

M6 implements the loop in `AgentOrchestrator` with **five independent, cooperative bounds** checked
between steps (never a `while(true)` without guards):

1. `maxIterations` (env `AGENT_MAX_ITERATIONS`, default 8) — LLM decision steps.
2. `maxToolCalls` (env `AGENT_MAX_TOOL_CALLS`, default 10) — tool executions; tracked separately from
   iterations so the two concepts never conflate.
3. A single wall-clock `deadline` (env `AGENT_TIMEOUT_SECONDS`, default 60), computed **once** at start
   and shared by the whole run — never reset per tool call.
4. `CancellationToken` (cooperative) — `DeadlineCancellationToken` unifies deadline + explicit cancel;
   the orchestrator exposes an external-token seam for M8/tests.
5. `LoopDetector` — fingerprint of tool + canonical arguments (see ADR-0016).

"Cooperative" means checks happen **between** steps; M6 does **not** hard-interrupt an in-flight LLM or
tool call. **M8 adds the hard enforcement**: per-tool hard timeout/interruption, the human-confirmation
workflow for side-effecting/irreversible tools, rate limiting, and retry hardening.

## Alternatives considered

- **Defer all bounds to M8** — rejected: it would ship an unbounded loop in M6, violating the
  no-unbounded-execution rule the moment the model can act.
- **Hard thread-interruption timeout in M6** — rejected as premature: cooperative checks give
  deterministic termination without the complexity/risk of interrupting provider/tool calls; that
  belongs with M8's enforcement layer.
- **One combined budget** — rejected: distinct iteration and tool-call budgets document intent and
  keep future non-1:1 actions clean.

## Consequences

- Every started run terminates and returns a structured `AgentResult` with run metadata.
- `GUARDRAILS.md` §2 and `ROADMAP.md` are updated to reflect the M6-cooperative / M8-hard split
  (this ADR is the record of that reconciliation).
- The `CancellationToken` seam and the single tool-execution point are where M8 attaches hard timeout,
  confirmation, and rate limiting without restructuring the loop.

## Links

- Spec §8–§16, §20 (R2). ADR-0013, ADR-0015, ADR-0016. `GUARDRAILS.md`, `ROADMAP.md`.
