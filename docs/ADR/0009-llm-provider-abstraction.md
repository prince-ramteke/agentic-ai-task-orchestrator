# ADR-0009: LLM provider abstraction and Ollama local-default strategy

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince Ramteke
- **Milestone:** M4 — Spring AI Integration

## Context
M4 introduces the application's LLM infrastructure — the foundation M5 (tools) and M6 (agent) build
on. Two forces shape the decision:

1. **No vendor coupling.** Per `CLAUDE.md` §3 and `.claude/rules/ai-agent.md`, features must reach
   the model only through the project's own abstraction, never a vendor SDK, so providers stay
   swappable and the model stays an *untrusted input* behind our code.
2. **Version compatibility.** The project is pinned to Spring Boot 3.4.1 (ADR-0001). Spring AI must
   be adopted without disturbing that (`CLAUDE.md` §32: do not upgrade Spring Boot merely to use a
   newer Spring AI).

## Decision
- Introduce **`com.prince.agentic.ai.llm.LlmClient`** as the single, provider-agnostic path to the
  model: `generate(prompt)`, `generateStructured(prompt, type)`, `info()`. It exposes only
  project-owned types (`LlmProviderInfo`) — no Spring AI type crosses the boundary.
- Implement it once with **`OllamaLlmClient`** (in `ai.llm.ollama`, the *only* package permitted to
  import `org.springframework.ai.*`, enforced by `ArchitectureBoundaryTest`), backed by **Spring AI**
  via **Ollama** running locally. `FakeLlmClient` implements the same interface for deterministic
  tests with no network.
- Adopt **Spring AI 1.0.9**, imported via **`spring-ai-bom`** in `dependencyManagement` with a
  `spring-ai.version` property. The 1.0.x line supports Spring Boot 3.3/3.4; the BOM governs only
  `spring-ai-*` artifact versions, so **the Boot parent stays 3.4.1** — no upgrade. Only
  `spring-ai-starter-model-ollama` is added (no tool-calling/vector/Redis modules).
- **Ollama is the local default** (`llama3.2`), configured by environment variables. Models are
  never auto-pulled (`spring.ai.ollama.init.pull-model-strategy: never`); the app boots even when
  Ollama is stopped, and a call then fails as a mapped `LLM_UNAVAILABLE` (503).
- A **cloud fallback provider is deferred** (documented future capability). `LLM_FALLBACK_ENABLED`
  stays `false`; no external provider is wired in M4 (`DATA_PRIVACY.md`).

## Alternatives considered
- **Call Spring AI's `ChatClient` directly from features** — rejected: couples every feature to the
  vendor SDK and violates the abstraction rule; nothing would be swappable or fake-testable.
- **Upgrade to Spring Boot 3.5 for Spring AI 1.1.x** — rejected: unnecessary. 1.0.9 is fully
  compatible with 3.4.1; upgrading would churn the whole platform for no M4 benefit (contradicts §32).
- **Spring AI 2.x / Boot 4** — rejected: too new; ecosystem alignment not settled for this project.
- **A cloud provider (OpenAI) as default or fallback now** — rejected: violates local-first privacy
  (`DATA_PRIVACY.md`); deferred until a privacy review explicitly enables it.

## Consequences
- Positive: providers are swappable behind one interface; the whole AI layer is testable without a
  model (FakeLlmClient); the boundary is enforced by a test; Boot stays put; M5/M6 build on
  `LlmClient` rather than a vendor SDK.
- Negative / follow-ups: a real provider outage returns 503/502 with no fallback (intended for M4).
  Provider-specific timeout wiring lives in `ai.config` and is version-sensitive. Live-model behavior
  is environment-gated (see `TESTING.md`), not part of CI.

## Links
- `docs/TECH_STACK.md`, `docs/AGENT_ARCHITECTURE.md`, `docs/DATA_PRIVACY.md`, ADR-0001, ADR-0010,
  `backend/src/main/java/com/prince/agentic/ai/llm/**`,
  `docs/superpowers/specs/2026-08-21-m4-spring-ai-ollama-design.md`.
