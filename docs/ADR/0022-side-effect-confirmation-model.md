# ADR-0022 — Side-Effect Confirmation Model

**Status:** Accepted · **Milestone:** M8 · **Date:** 2026-08-22

## Context
A `SIDE_EFFECTING`/`HIGH_RISK` tool must not run merely because the LLM proposed it. The user must
confirm. We had to choose how confirmation interacts with the single-shot orchestration loop.

## Decision
- **Halt, don't resume.** When a guardrail requires confirmation, the run terminates at
  `PENDING_CONFIRMATION` and returns the exact proposed action. There is **no automatic LLM-loop
  resume** after confirmation; a later user turn continues the conversation separately. (Persisting and
  replaying full loop state was rejected as heavier than M8's scope and closer to M9 durable execution.)
- **Execute exactly once.** The confirm endpoint runs only the stored, fingerprint-bound action a
  single time through the normal `ToolExecutor` gates. It accepts **no client argument body** — the
  stored action is what runs, so argument mutation is structurally impossible.
- **Layering.** The orchestrator produces the `PendingAction`; the conversation layer (which owns the
  `conversationId`) creates the stored confirmation; a dedicated confirm service executes it. The
  orchestrator stays Redis-free, depending only on the `GuardrailEngine`/`RateLimiter` abstractions.

## Consequences
- Simple, safe, no durable execution state (that is M9).
- Confirmation authorizes intent; it never bypasses authorization (the executed action re-runs every M5
  gate).
- Verified by `AgentOrchestratorTest`, `AgentConfirmationServiceTest`, `AgentGuardrailConfirmationIT`.
