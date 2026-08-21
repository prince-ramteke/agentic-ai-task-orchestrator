# Milestone 4 — Spring AI + Ollama Integration — Design Specification

- **Date:** 2026-08-21
- **Milestone:** M4 — Spring AI Integration
- **Status:** Approved design (implementation not started)
- **Author/Deciders:** Prince (owner) + Claude
- **Uses:** M1 foundation (`ApiException`/`GlobalExceptionHandler`, `ApiError`, `/api/v1` conventions, Actuator + Micrometer, profiles) · M2 security (deny-by-default `SecurityConfig`, `AuthenticatedUser` principal) · M3 domain (untouched — the AI layer must stay independent of `TaskService`/`CustomerService`).

> This spec is the source of truth for the M4 implementation plan. It resolves every "decide"
> point in the milestone brief and aligns with `CLAUDE.md`, `.claude/rules/ai-agent.md`,
> `docs/TECH_STACK.md`, `docs/AGENT_ARCHITECTURE.md`, `docs/DATA_PRIVACY.md`,
> `docs/OBSERVABILITY.md`, `docs/SECURITY.md`, `docs/API.md`, `docs/TESTING.md`, and
> `docs/ROADMAP.md`. Where those docs and this spec would disagree, this spec lists the doc
> edits required so nothing is left contradictory.

---

## 1. Objective & scope

Introduce a **clean, provider-agnostic, testable LLM infrastructure layer** so later milestones
(M5 tools, M6 agent) can call the model **only** through the project's own abstraction —
`LlmClient` — never a vendor SDK, never from a feature directly. M4 builds the *foundation*, not
the agent.

Target architecture (unchanged from the milestone brief):

```
REST/API layer  →  AiService  →  LlmClient  →  OllamaLlmClient  →  Ollama  →  local LLM
                                     ↑
                                FakeLlmClient (tests only)
```

**In scope:** Spring AI + Spring AI Ollama starter; the `LlmClient` provider abstraction; an
Ollama-backed implementation; an application-level `AiService`; prompt management; typed/structured
output with application-side validation; an AI-specific exception model integrated with the
existing error envelope; timeout + conservative retry + clean failure path; a deterministic
`FakeLlmClient`; unit + Spring-context integration tests; a profile-gated **optional** live Ollama
test; a minimal authenticated demo endpoint; observability hooks (metrics + metadata logging);
docs; ADRs.

**Explicitly NOT in scope (future milestones — must not be built now):** agent orchestration /
ReAct loop / planner (M6); tool registry, tool/function calling, Task/Customer tools (M5); Redis /
conversation persistence (M7); guardrails engine, confirmation flow, loop detection (M8); agent
audit system (M9); Prometheus/Grafana dashboards (M10); Kafka; frontend (M13); multi-agent;
cloud-provider fallback (documented as future, **not** implemented). No `ToolRegistry`,
`Orchestrator`, `@Tool`/function-calling wiring, or `/api/v1/agent` endpoint is introduced.

**Boundary guarantee (hard):** the `ai` feature package must not depend on `task`, `customer`,
their repositories, or `EntityManager`/`JdbcTemplate`. No database access from the AI layer. This
is enforced by review and by a dependency-direction test (see §10).

---

## 2. Confirmed decisions (authoritative)

| # | Decision | Choice |
|---|---|---|
| D1 | LLM framework | **Spring AI**, primary AI framework (per `TECH_STACK.md`). No competing AI library. |
| D2 | Spring AI version | **1.0.9** (latest 1.0.x), imported via `spring-ai-bom` in `dependencyManagement` + a `spring-ai.version` property. The 1.0.x line supports Spring Boot 3.3/3.4; **the Boot parent stays 3.4.1** (the BOM governs only `spring-ai-*` artifacts). No Boot upgrade. (1.1.x needs Boot 3.5, 2.x needs Boot 4 — both rejected.) |
| D3 | Provider abstraction | `com.prince.agentic.ai.llm.LlmClient` is the **only** path to the model. The rest of the app depends on it, never on `OllamaChatModel`/`ChatClient`. |
| D4 | `LlmClient` contract | Minimal: `String generate(String prompt)`; `<T> T generateStructured(String prompt, Class<T> type)`; `LlmProviderInfo info()`. No giant generic framework. |
| D5 | Provider metadata type | `LlmProviderInfo` = our own `record(String provider, String model)`. **No** Spring AI type crosses the abstraction. |
| D6 | Ollama impl | `ai.llm.ollama.OllamaLlmClient` wraps Spring AI `ChatClient` (built on the auto-configured `OllamaChatModel`); maps Spring AI/transport failures to our exception model. |
| D7 | Provider selection | `@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)` on the Ollama impl. Exactly one `LlmClient` bean is active. Tests supply `FakeLlmClient` instead. |
| D8 | Default local model | **`llama3.2`** (already pulled locally; small, fast, Spring-AI-Ollama supported). Overridable via `OLLAMA_MODEL`. |
| D9 | Model pull policy | `spring.ai.ollama.init.pull-model-strategy: never` — **never auto-download** a model. A missing model surfaces as a provider error at request time, not a silent multi-GB pull. |
| D10 | App boot vs Ollama down | The app **must boot with Ollama stopped** (no startup model call, given D9). Model calls fail only at request time → mapped to `LLM_UNAVAILABLE` (503). Verified by the context-load test (no live Ollama). |
| D11 | Structured output | **Spring AI's structured-output converter** (`ChatClient….entity(Class)` / `BeanOutputConverter`) produces the typed object; **then** `AiService` re-validates every field with Bean Validation. Model output is untrusted even after it parses. |
| D12 | Repair/retry of bad output | On validation failure, **one** bounded "repair" re-ask (re-prompt including the validation error). If it still fails → `LlmInvalidOutputException` (422). No unbounded loop. |
| D13 | Transient-failure retry | Spring AI's built-in `RetryTemplate` (`spring.ai.retry.max-attempts: 2`, backoff, **`on-client-errors: false`** so 4xx are not retried). Never retry validation failures or invalid prompts. |
| D14 | Timeouts | Explicit **connect** + **read** timeouts applied to Spring AI's Ollama HTTP client via a `ClientHttpRequestFactorySettings`/customizer bean, driven by `OLLAMA_TIMEOUT_SECONDS` (default 60s). No indefinite wait. |
| D15 | Exception model | `LlmException extends ApiException` (abstract) + 4 concrete types (D16). Renders through the **existing** `GlobalExceptionHandler` `ApiException` branch — no second error system, no new handler. |
| D16 | Exception → HTTP map | `LlmUnavailableException`→**503** `LLM_UNAVAILABLE` (connection refused / model missing) · `LlmTimeoutException`→**504** `LLM_TIMEOUT` · `LlmProviderException`→**502** `LLM_PROVIDER_ERROR` (other provider/runtime) · `LlmInvalidOutputException`→**422** `LLM_INVALID_OUTPUT`. |
| D17 | Application service | `ai.AiService` orchestrates prompt→`LlmClient`→validate→map-errors. It must **not** know `TaskRepository`/`CustomerRepository`, query the DB, authorize, or execute tools. |
| D18 | Prompt management | `ai.prompt.PromptService` renders **template files** under `resources/prompts/*.st` via Spring AI `PromptTemplate`. Untrusted user input is injected only into a clearly delimited variable, never into the instruction text. No large prompt strings in controllers/services. |
| D19 | Structured demo type | **Two records, deliberately split.** `ai.dto.AiClassificationResult` = the **LLM-target** `{ClassificationCategory category, ClassificationPriority priority, String summary}` with Bean Validation — this is the type passed to `generateStructured` and the `@Valid` target (the converter's JSON schema is derived from *only* these model-produced fields). `ai.dto.AiClassificationResponse` = the **API response** `{category, priority, summary, String model, String provider}`, assembled by `AiService` from the validated result + `LlmClient.info()`. Metadata is never asked of the model. Both are **M4-owned** and independent of the `Task` entity. |
| D20 | Demo endpoints | `POST /api/v1/ai/generate` (plain text) **and** `POST /api/v1/ai/classify` (typed+validated). Both authenticated. Named `ai`, **not** `agent`. |
| D21 | Request DTOs | `AiGenerateRequest{ @NotBlank @Size(min=1,max=4000) String prompt }`; `AiClassifyRequest{ @NotBlank @Size(min=1,max=4000) String text }`. Bounded request size; no unlimited text. |
| D22 | Response DTOs | `AiGenerateResponse{ String content, String model, String provider }`; classify returns `AiClassificationResponse` (D19). **No** raw Spring AI provider object is ever returned. |
| D23 | Logging | Metadata only: `provider`, `model`, `durationMs`, `status/outcome`, request `traceId`. **Never** the full prompt or full model response, even at DEBUG. |
| D24 | Metrics | Micrometer (already on the classpath via Actuator): `llm.request.duration` (timer), `llm.request.result` (counter, tags `provider,model,outcome`), `llm.provider.errors` (counter). Bounded label cardinality. Token usage recorded **only if** Ollama/Spring AI returns it; otherwise documented UNAVAILABLE (not fabricated). |
| D25 | Fallback | Clean failure path only: Ollama unavailable → `LlmClient` failure → `LlmException` → safe envelope. **No** cloud fallback implemented; documented as future (`LLM_FALLBACK_ENABLED` stays `false`, `openai` provider not wired). |
| D26 | Test isolation | Unit tests mock `LlmClient`. Spring-context tests wire a **`FakeLlmClient`** (test source) so the full HTTP path works with **no Ollama**. CI never needs a model. |
| D27 | Live Ollama test | One `OllamaLlmClientLiveIT`, **gated** (profile `ai-it` and/or `-Dllm.live.ollama=true`), excluded from normal `mvn verify`. Documented run command. Not claimed VERIFIED unless actually executed. |
| D28 | Coverage gate | Keep the existing JaCoCo `BUNDLE ≥ 0.75` gate. Add excludes for provider/config infra: `com/prince/agentic/ai/config/**`, `com/prince/agentic/ai/llm/ollama/**` (like the existing `config/**`). `**/dto/**` already excluded. `AiService`, `PromptService`, exceptions, validation are fully covered by real tests — no coverage-padding tests. |
| D29 | ADRs | **ADR-0009** LLM provider abstraction & Ollama-local-default strategy (incl. Spring AI 1.0.9 / Boot-3.4 compat rationale, fallback deferred). **ADR-0010** Structured LLM output strategy (Spring AI converter + application-side validation, output-as-untrusted). Two ADRs — one decision each. |

---

## 3. Package & file layout (package-by-feature)

New production code lives under one cohesive feature package `com.prince.agentic.ai`; the
provider abstraction is isolated in the `ai.llm` sub-boundary.

```
backend/src/main/java/com/prince/agentic/ai/
  AiController.java                     # POST /api/v1/ai/generate, /classify  (thin)
  AiService.java                        # orchestrate prompt→LlmClient→validate→map errors
  dto/
    AiGenerateRequest.java              # @NotBlank @Size prompt
    AiGenerateResponse.java             # content, model, provider
    AiClassifyRequest.java              # @NotBlank @Size text
    AiClassificationResult.java         # category, priority, summary  — LLM-target + @Valid target (M4-owned)
    AiClassificationResponse.java       # category, priority, summary, model, provider — API response (assembled)
    ClassificationCategory.java         # enum BUG, FEATURE, QUESTION, OTHER  (M4-owned)
    ClassificationPriority.java         # enum LOW, MEDIUM, HIGH             (M4-owned)
  prompt/
    PromptService.java                  # render resources/prompts/*.st via Spring AI PromptTemplate
  llm/
    LlmClient.java                      # THE abstraction (interface)
    LlmProviderInfo.java                # record(provider, model) — our type, not Spring AI
    exception/
      LlmException.java                 # abstract extends ApiException
      LlmUnavailableException.java      # 503 LLM_UNAVAILABLE
      LlmTimeoutException.java          # 504 LLM_TIMEOUT
      LlmProviderException.java         # 502 LLM_PROVIDER_ERROR
      LlmInvalidOutputException.java    # 422 LLM_INVALID_OUTPUT
    ollama/
      OllamaLlmClient.java              # wraps Spring AI ChatClient; maps errors  (coverage-excluded)
  config/
    LlmProperties.java                  # @ConfigurationProperties(prefix="llm")   (coverage-excluded)
    AiConfig.java                       # ChatClient + timeout customizer + MeterRegistry wiring (coverage-excluded)

backend/src/main/resources/prompts/
  classify.st                           # classification instruction + {format} + delimited {input}
  generate.st                           # thin wrapper (delimits {input}); text pass-through

backend/src/test/java/com/prince/agentic/ai/
  support/FakeLlmClient.java            # deterministic: valid | invalid-output | timeout | provider-error | structured
  AiServiceTest.java                    # text, structured-happy, validation→repair→ok, repair-exhausted→422, provider error→map
  prompt/PromptServiceTest.java         # template renders; input delimited; instructions not overridable
  AiControllerTest.java                 # web layer: 200 (text+classify), 400 (blank/too-long), 401, error mapping (503/502/422)
  AiIntegrationTest.java                # @SpringBootTest + FakeLlmClient bean: context loads, endpoints end-to-end, NO Ollama
  llm/ollama/OllamaLlmClientLiveIT.java # GATED live Ollama call (profile ai-it / -Dllm.live.ollama=true); excluded from verify
  ArchitectureBoundaryTest.java         # ai.* must not reference task/customer/repository/EntityManager
```

**Files modified (not created):**
```
backend/pom.xml                         # spring-ai-bom + spring-ai-starter-model-ollama; JaCoCo excludes; (optional ai-it profile)
backend/src/main/resources/application.yml        # llm.* + spring.ai.ollama.* + spring.ai.retry.*
backend/src/main/resources/application-local.yml   # dev model/base-url defaults, DEBUG for ai package
backend/src/main/resources/application-test.yml    # llm.provider stub so Ollama autoconfig is inert in tests
```

---

## 4. The `LlmClient` contract (the crux)

```java
package com.prince.agentic.ai.llm;

/** The only path to the language model. Providers are swappable; features never see a vendor SDK. */
public interface LlmClient {

    /** Free-form text generation. Throws an {@link com.prince.agentic.ai.llm.exception.LlmException} on failure. */
    String generate(String prompt);

    /**
     * Structured generation into a typed object. The returned object is parsed by the provider's
     * structured-output converter but is NOT yet trusted — the caller (AiService) validates it.
     */
    <T> T generateStructured(String prompt, Class<T> type);

    /** Provider + model identity for response metadata and logging. Never a vendor type. */
    LlmProviderInfo info();
}
```

Rationale: two generation methods (text + structured) plus identity is enough for M4 and for the
M6 agent to build on. No streaming, no options bag, no message history — YAGNI until a milestone
needs them. Adding capability later is an interface extension, not a rewrite (that is the point of
the abstraction).

---

## 5. Request flows

**Text — `POST /api/v1/ai/generate`:**
```
Controller (@Valid AiGenerateRequest, @AuthenticationPrincipal)
  → AiService.generateText(prompt)
      → PromptService.render("generate", input=prompt)         (input delimited)
      → LlmClient.generate(renderedPrompt)                     (timeout + retry inside provider)
      → build AiGenerateResponse{content, model, provider}     (from LlmClient.info())
      → log metadata + record metrics
  → 200 AiGenerateResponse
```

**Structured — `POST /api/v1/ai/classify`:**
```
Controller (@Valid AiClassifyRequest)
  → AiService.classify(text)
      → PromptService.render("classify", input=text)           (includes {format} from converter)
      → LlmClient.generateStructured(prompt, AiClassificationResult.class)
      → Validator.validate(result)                             (Bean Validation — untrusted output)
          ├─ valid   → assemble AiClassificationResponse (result + info() model/provider) → return
          └─ invalid → ONE repair re-ask with the error
                          ├─ valid  → assemble + return
                          └─ invalid → throw LlmInvalidOutputException (422)
      → log metadata + record metrics
  → 200 AiClassificationResponse
```

**Failure mapping (in `OllamaLlmClient` / `AiService`):**
| Cause | Exception | HTTP | Code |
|---|---|---|---|
| Connection refused / Ollama down / model not found | `LlmUnavailableException` | 503 | `LLM_UNAVAILABLE` |
| Read timeout exceeded | `LlmTimeoutException` | 504 | `LLM_TIMEOUT` |
| Other provider/runtime error (5xx, malformed transport) | `LlmProviderException` | 502 | `LLM_PROVIDER_ERROR` |
| Structured output fails validation after repair | `LlmInvalidOutputException` | 422 | `LLM_INVALID_OUTPUT` |

All four extend `ApiException`, so the existing `GlobalExceptionHandler` renders the standard
`ApiError` envelope `{timestamp,status,error,message,path,traceId}` unchanged.

---

## 6. Configuration (env-driven; keys already in `.env.example`)

`application.yml` (base) additions:
```yaml
llm:
  provider: ${LLM_PROVIDER:ollama}          # ollama | openai(future, not wired)
  request-timeout-seconds: ${OLLAMA_TIMEOUT_SECONDS:60}
  ollama:
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    model: ${OLLAMA_MODEL:llama3.2}
    temperature: ${OLLAMA_TEMPERATURE:0.2}

spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      init:
        pull-model-strategy: never          # never auto-pull a model (D9)
      chat:
        options:
          model: ${OLLAMA_MODEL:llama3.2}
          temperature: ${OLLAMA_TEMPERATURE:0.2}
    retry:
      max-attempts: 2
      on-client-errors: false               # do not retry 4xx (D13)
```
`application-test.yml`: set `llm.provider: fake` (or leave `spring.ai.ollama` unused) so the
Ollama auto-config never attempts a network call during tests; `FakeLlmClient` is the active
`LlmClient`. No new secrets. `.env.example` already lists `LLM_PROVIDER`, `OLLAMA_BASE_URL`,
`OLLAMA_MODEL`, `LLM_FALLBACK_ENABLED`, `OPENAI_API_KEY` — spec adds `OLLAMA_TEMPERATURE` and
confirms `OLLAMA_TIMEOUT_SECONDS` usage; both get documented in `DEPLOYMENT.md`.

---

## 7. Prompt management

- Templates are files under `resources/prompts/` (`classify.st`, `generate.st`), rendered by
  Spring AI `PromptTemplate`. They are versionable (in git), testable, and readable.
- **Injection posture (M4 scope):** untrusted user text is bound only to a delimited `{input}`
  variable, never concatenated into the instruction portion. This is defense-in-depth documentation,
  **not** the full guardrails/prompt-injection system (M8). The spec records: *LLM output is
  untrusted; prompts are not a security boundary; deeper guardrails arrive in M8.*
- No business rules are embedded in prompt text where deterministic Java is appropriate.

---

## 8. Security (M4 surface)

- `/api/v1/ai/**` is authenticated by the existing deny-by-default `SecurityConfig`; **no** change
  to `PUBLIC_ENDPOINTS`. A security test asserts 401 for unauthenticated access.
- Input validated (`@NotBlank`, `@Size ≤ 4000`) → 400 via the global handler.
- No DB access, no ownership decisions, no tool access in the AI layer (M4 sends **no** Task/Customer
  data to the model). Secrets stay in env; none logged. No raw provider object leaked in responses.
- Prompts/responses are not logged in full (D23). Local-first: `LLM_FALLBACK_ENABLED=false`.

---

## 9. Observability

- Structured SLF4J at `AiService`: INFO on completion (`ai.generate`/`ai.classify` with
  `provider`, `model`, `durationMs`, `outcome`), WARN on repair/retry, mapped errors at WARN/ERROR
  per status class. Metadata only.
- Micrometer meters per D24 via the existing `MeterRegistry` (Actuator/micrometer-core already on
  the classpath). No new endpoint exposure, no Prometheus/Grafana (M10). Health contribution for
  the LLM provider is **out of scope** for M4 (documented; belongs with M10/M12 compose) to avoid a
  health check that probes a possibly-down local Ollama.

---

## 10. Testing strategy

- **Unit (surefire / no network):** `AiServiceTest` (mock `LlmClient`) — text path; structured
  happy; validation-fail → repair → success; repair-exhausted → `LlmInvalidOutputException`;
  provider error → correct `LlmException` subtype. `PromptServiceTest` — templates render, input
  delimited, instruction text present. `FakeLlmClient` self-test of its modes.
- **Web slice:** `AiControllerTest` — 200 (text + classify), 400 (blank / >4000 chars), 401
  (unauthenticated), and error mapping (503/504/502/422) with the standard envelope.
- **Spring context IT (no Ollama):** `AiIntegrationTest` (`@SpringBootTest`, `FakeLlmClient` wired)
  — context loads with AI auto-config present, both endpoints work end-to-end. Proves the app boots
  and serves AI with **Ollama absent** (D10).
- **Architecture test:** `ArchitectureBoundaryTest` — `com.prince.agentic.ai.*` references none of
  `task`, `customer`, `*Repository`, `EntityManager`, `JdbcTemplate` (guards the isolation rule).
- **Live Ollama (gated, optional):** `OllamaLlmClientLiveIT` — real request to a running Ollama,
  asserts a non-empty response and a valid structured classification. Runs only under profile
  `ai-it` / `-Dllm.live.ollama=true`. Command documented in `TESTING.md` and `backend/README.md`.
- **Gate:** `./mvnw clean test` and `./mvnw verify` green with the 0.75 BUNDLE gate held; new infra
  excluded (D28). No live LLM in CI.

---

## 11. Dependencies added (M4 only)

- `dependencyManagement`: `org.springframework.ai:spring-ai-bom:1.0.9` (scope `import`), via a
  `<spring-ai.version>1.0.9</spring-ai.version>` property.
- `dependencies`: `org.springframework.ai:spring-ai-starter-model-ollama` (version from BOM).
- **Not added:** Redis, Kafka, vector DB, Spring AI tool-calling/function modules, any agent
  framework. Spring AI is the single AI framework. 1.0.9 is GA on Maven Central (no Spring milestone
  repo required — to be confirmed at implementation; if a repo is needed it is added explicitly).

---

## 12. Documentation updates (truthful, labeled PLANNED/IMPLEMENTED/VERIFIED)

`TECH_STACK.md` (Spring AI 1.0.9 line + ADR link) · `AGENT_ARCHITECTURE.md` (note the `LlmClient`
foundation now exists; agent still M6) · `TOOL_SYSTEM.md` (unchanged contract; note tools arrive
M5 above this layer) · `DATA_PRIVACY.md` (M4 status: local-first, no DB data sent, prompts/outputs
not logged in full) · `SECURITY.md` (AI endpoint auth + input validation + output-untrusted) ·
`OBSERVABILITY.md` (M4: llm.* metrics + metadata logging) · `PERFORMANCE.md` (LLM latency/timeout,
no fabricated numbers) · `API.md` (the two `/api/v1/ai/*` endpoints + `LLM_*` error codes) ·
`TESTING.md` (fake-based tests + gated live IT command) · `DEPLOYMENT.md` + `.env.example`
(`OLLAMA_TEMPERATURE`, `OLLAMA_TIMEOUT_SECONDS`) · `ROADMAP.md` (M4 → status/notes) ·
`CHANGELOG.md` · `README.md` + `backend/README.md` (how to configure a model / run the live IT).
New: `ADR/0009-*`, `ADR/0010-*`, and `ADR/README.md` index rows.

---

## 13. Risks & limitations

- **Live Ollama not guaranteed** — daemon is installed but not running; CI/`verify` are fake-based.
  Live verification is **optional** and reported honestly (VERIFIED only if actually run).
- **Spring AI Ollama startup behavior** — must confirm `pull-model-strategy: never` prevents any
  startup network call so the app boots with Ollama down (D10). If auto-config still probes, make
  `OllamaLlmClient`/`ChatClient` lazy. This is verified by `AiIntegrationTest` at implementation.
- **Token usage may be unavailable** from Ollama via Spring AI; recorded only if present, never
  fabricated (D24).
- **Timeout wiring** — applying connect/read timeouts to Spring AI's Ollama client is version-specific;
  the exact hook (`ClientHttpRequestFactorySettings` vs a `RestClientCustomizer`) is confirmed
  against 1.0.9 during implementation. The *behavior* (bounded wait → `LLM_TIMEOUT`) is fixed here.
- **No cloud fallback** — a hard provider outage returns 503/502; that is the intended M4 behavior.

---

## 14. Definition of Done (M4)

Abstraction is the only path to the model · Ollama impl behind it · `FakeLlmClient` makes tests
deterministic with no network · structured output validated as untrusted with bounded repair ·
AI-specific exceptions render through the existing envelope · demo endpoints authenticated +
validated + documented · `./mvnw clean test` and `./mvnw verify` green at the 0.75 gate ·
docs + 2 ADRs updated · no secrets committed · no agent/tool/Redis/guardrail code introduced ·
AI layer provably independent of Task/Customer. Live Ollama = VERIFIED only if actually executed;
otherwise IMPLEMENTED — LIVE OLLAMA NOT VERIFIED.
