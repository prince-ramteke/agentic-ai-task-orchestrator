# ADR-0011: Tool abstraction and registry

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M5 — Tool Registry & Tool Execution Framework

## Context
M6 will let an LLM-driven agent take actions. Per `CLAUDE.md` and `.claude/rules/ai-agent.md`, the
model must never touch repositories, `EntityManager`, arbitrary methods, or code — it may only name an
**explicitly registered** capability with validated arguments. M5 builds that deterministic
infrastructure *below* the agent, and it must be reusable and independent of the LLM layer.

## Decision
- **`Tool<I, O>`** — `ToolDescriptor descriptor()` + `O execute(ToolExecutionContext, I)`. The handler
  returns **raw `O`** and throws typed exceptions; the **`ToolExecutor` returns a `ToolResult<O>`**
  envelope (`toolName, success, data, ToolError, durationMs`) — the structured observation the future
  agent needs.
- **Discovery = plain Spring bean injection.** Every tool is a `@Component`; `ToolRegistry` receives
  `List<Tool<?,?>>` and indexes by `descriptor().name()`. **No `@AgentTool` annotation** — metadata
  lives only in the descriptor.
- **Registry is fail-fast + immutable.** Invalid/duplicate tools throw `ToolRegistrationException`,
  failing boot; after construction the index is unmodifiable → O(1) lookup, thread-safe. No runtime
  registration, no dynamic plugin loading.
- **Names are dot-namespaced and stable** (`task.get`, `math.calculate`), never Java class names.
  Versioning is a descriptor field (default `"1"`), not encoded in the name.
- **Schema strategy:** the descriptor exposes `Class<I>/Class<O>`. **M5 builds no JSON-schema engine.**
  M6's Spring AI adapter derives the schema from `inputType`.
- **Spring AI boundary:** the tool subsystem imports no `org.springframework.ai.*` / `ai.*` (enforced
  by `ToolArchitectureBoundaryTest`). The Spring AI adapter (`ToolDescriptor` → tool definition;
  `ToolExecutor` behind a `FunctionToolCallback`) is **M6 code**.

## Alternatives considered
- **`@AgentTool` annotation framework** — rejected: duplicates the descriptor; more ceremony, no gain.
- **Executor returns raw `O`** — rejected: M6 would re-wrap every call to obtain structured
  success/error/duration for observations.
- **Build a JSON-schema engine now** — rejected: Spring AI generates schemas from a type in M6; YAGNI.
- **Runtime/dynamic tool registration** — rejected: an immutable startup registry is simpler, safer
  (no registry poisoning), and sufficient.

## Consequences
- Positive: a small, strongly-typed, framework-independent tool core that M6 bridges to Spring AI; a
  reusable contract test (`AbstractToolContractTest`) every future tool inherits; O(1), thread-safe
  lookup; boot fails loudly on a malformed registry.
- Negative / follow-ups: an unchecked cast to `Tool<Object,Object>` is contained in the executor after
  binding; the Spring AI adapter is deferred to M6.

## Links
- `docs/TOOL_SYSTEM.md`, `docs/AGENT_ARCHITECTURE.md`, ADR-0012,
  `backend/src/main/java/com/prince/agentic/tool/**`,
  `docs/superpowers/specs/2026-08-21-m5-tool-system-design.md`.
