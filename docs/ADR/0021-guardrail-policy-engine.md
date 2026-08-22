# ADR-0021 — Guardrail Policy Engine

**Status:** Accepted · **Milestone:** M8 · **Date:** 2026-08-22

## Context
M6 gives the orchestrator cooperative bounds; M5 classifies every tool's risk. M8 must turn these into
a single, backend-authoritative gate that decides — before any effect — whether a model-proposed
action may run. We need this to be deterministic, testable, and easy to extend, without building a
general-purpose rules DSL (out of scope).

## Decision
- A `GuardrailEngine` sits between the validated `AgentDecision` and `ToolExecutor`. It returns exactly
  one of three outcomes: `ALLOW`, `DENY`, `REQUIRE_CONFIRMATION`.
- The engine resolves the authoritative `ToolDescriptor` from the `ToolRegistry`; **risk comes from the
  descriptor, never from the model**. An unknown tool → `ALLOW` (deferred to `ToolExecutor`'s
  `TOOL_NOT_FOUND`, so no effect can occur), preserving M6 recovery.
- Policy is a list of ordered `GuardrailPolicy` beans; the engine runs them ascending and the **first
  non-`ALLOW` wins**. Adding a policy is adding a bean — the engine is closed for modification.
- `evaluate` is **pure** (only emits a metric), so outcomes are fully deterministic under test.
- The engine **adds policy only**. It never re-resolves, authenticates, binds, validates, or authorizes
  a tool — those M5 gates remain in `ToolExecutor` and run exactly once (no duplication).

## Consequences
- Clear separation: M8 policy vs. M5 execution gates vs. M6 bounds.
- Extensible without editing core logic; each policy is independently unit-tested.
- Verified by `RiskPolicyTest`, `ArgumentSafetyPolicyTest`, `DefaultGuardrailEngineTest`.
