# ADR-0010: Structured LLM output strategy

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M4 — Spring AI Integration

## Context
The agent (M6) and several tools (M5) will need the model to return **typed** data, not free text.
M4 establishes how structured output is produced and — critically — how it is trusted. The governing
rule (`.claude/rules/ai-agent.md`): *treat all model output as untrusted; validate against a typed
schema; repair/retry on malformed output.* A model that emits JSON is not a model that emits
**correct** JSON: a live `llama3.2` run during M4 returned an out-of-range enum
(`priority: "FEATURE"`), which proves the point.

## Decision
- Use **Spring AI's structured-output converter** (`ChatClient….entity(Type.class)` /
  `BeanOutputConverter`) to parse model output into a typed record. The converter injects the JSON
  schema/format instruction, derived from the target type's fields only.
- The structured **target type is separate from the API response type**: the model fills
  `AiClassificationResult{category, priority, summary}` (the converter schema is exactly these
  fields); the service assembles the API `AiClassificationResponse` by adding server-side
  `model`/`provider` metadata. The model is never asked to produce metadata.
- **Re-validate every structured result with Bean Validation** in `AiService` after it parses —
  parsing is not acceptance.
- **Bounded repair:** on invalid output — whether the converter *threw* (e.g. an out-of-range enum,
  mapped to `LLM_INVALID_OUTPUT`) or *returned* a Bean-Validation-failing object — re-ask **once**
  with the error. If still invalid, fail with `LlmInvalidOutputException` → **422 LLM_INVALID_OUTPUT**.
  Total model calls per request are bounded to two. This is not retrying transient transport errors
  (handled separately by Spring AI's `RetryTemplate`); it never loops.

## Alternatives considered
- **Trust the parsed object** (no validation) — rejected: violates the untrusted-output rule; the
  live run shows real models emit invalid values that parse or throw unpredictably.
- **Hand-rolled Jackson parsing + a prompt asking for JSON** — rejected: reinvents Spring AI's
  converter and its schema generation for no benefit.
- **Unbounded repair loop until valid** — rejected: unbounded model usage; a stubborn small model
  would never terminate. One repair, then a clean 422.

## Consequences
- Positive: uniform, typed, validated outputs that M5/M6 can rely on; malformed output is a defined,
  observable 422, not a leak or a hang; the trust boundary is explicit and tested (unit tests +
  a gated live IT).
- Negative / follow-ups: a small local model may fail even after repair (→ 422); that is honest
  behavior, not a bug. Deeper prompt-injection/guardrail handling of output is deferred to M8.

## Links
- `.claude/rules/ai-agent.md`, `docs/AGENT_ARCHITECTURE.md`, ADR-0009,
  `backend/src/main/java/com/prince/agentic/ai/AiService.java`,
  `backend/src/main/java/com/prince/agentic/ai/llm/ollama/OllamaLlmClient.java`,
  `docs/superpowers/specs/2026-08-21-m4-spring-ai-ollama-design.md`.
