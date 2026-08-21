# ADR-0013: Agent Decision Contract

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince + Claude

## Context

Milestone 6 introduces the agent: a backend loop that uses the LLM to decide the next step and then
drives the M5 tool framework. The loop needs a control protocol between the model and the
orchestrator. Two failure modes must be excluded by construction: (a) the model returning free-form
prose that the backend then parses heuristically, and (b) the model driving execution directly via a
framework's automatic tool-calling, which would move the loop, budgets, and policy out of our code.
The M4 LLM layer already provides structured output (`LlmClient.generateStructured`) with a single
bounded repair (`AiService.classify`/`attempt`).

## Decision

The LLM returns a **typed decision**, `AgentDecision(AgentAction action, String response, String tool,
Map<String,Object> arguments)` with `AgentAction ∈ {FINAL, TOOL_CALL}`, produced through the existing
M4 `LlmClient.generateStructured(prompt, AgentDecision.class)` — no new `LlmClient` method. Each
iteration renders a fresh prompt carrying prior observations. `AgentDecisionValidator` enforces
cross-field validity (FINAL requires `response` and no `tool`; TOOL_CALL requires `tool` and no
`response`). A malformed or invalid decision triggers exactly **one** bounded repair re-ask (mirroring
M4); a second failure raises `AgentInvalidDecisionException`. Provider/timeout/unavailable errors are
not repaired — they propagate.

## Alternatives considered

- **Free-prose control protocol** parsed by the backend — rejected: brittle, unbounded parsing surface,
  and it blurs the untrusted-planner boundary.
- **Spring AI automatic tool-calling as the loop** — rejected: it would own iteration/tool-call
  budgets, loop detection, the deadline, observation bounds, and cancellation. M6 must own those
  (see ADR-0014). Spring AI stays confined to model invocation + structured conversion inside
  `OllamaLlmClient`.
- **A new `LlmClient.generateStructured` overload carrying conversation history** — rejected as
  unnecessary: rendering observations into each prompt reuses M4 unchanged.

## Consequences

- The model's output is always parsed into a typed object and validated before the orchestrator acts.
- Two-level argument validation holds: the decision envelope is validated here; tool arguments are
  re-bound and re-validated by the M5 `ToolExecutor` (a spoofed `ownerId`/`userId` → `TOOL_INVALID_INPUT`).
- Adding a future action (e.g. `ASK_CLARIFICATION`) is a contained change to the enum + validator +
  orchestrator; deferred until a real need (M7+).

## Links

- Spec: `docs/superpowers/specs/2026-08-21-m6-agent-orchestration-design.md` §5–§6.
- ADR-0009 (LLM provider abstraction), ADR-0010 (structured LLM output), ADR-0014, ADR-0015.
