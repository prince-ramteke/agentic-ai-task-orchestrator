# Milestone 6 — Agent Orchestration — Design Specification

- **Date:** 2026-08-21
- **Milestone:** M6 — Agent Orchestration
- **Status:** Approved design (implementation not started)
- **Author/Deciders:** Prince (owner) + Claude
- **Uses:** M2 security (`AuthenticatedUser`, `@AuthenticationPrincipal`, `RoleNames`, deny-by-default) · M3 domain (`TaskService`/`CustomerService` — reached **only** through M5 tools, never directly) · M4 LLM layer (`LlmClient.generateStructured`, structured-output + one-repair pattern, `LlmException` hierarchy, `FakeLlmClient`) · M5 tool framework (`ToolRegistry`, `ToolExecutor`, `ToolDescriptor`, `ToolExecutionContext`, `ToolResult`, `ToolRiskLevel`) · M1 error model (`ApiException`/`GlobalExceptionHandler`/`ApiError`, `/api/v1`).

> Source of truth for the M6 implementation plan. Resolves every "decide" point in the milestone
> brief and aligns with `CLAUDE.md`, `.claude/rules/ai-agent.md`, `docs/AGENT_ARCHITECTURE.md`,
> `docs/GUARDRAILS.md`, `docs/TOOL_SYSTEM.md`, `docs/SECURITY.md`, `docs/API.md`, `docs/TESTING.md`,
> `docs/OBSERVABILITY.md`, `docs/ROADMAP.md`. Where a doc and this spec disagree, §20 (Reconciliations)
> lists the exact doc edit required so nothing is left contradictory.

---

## 1. Objective & scope

Build the **backend-controlled agent execution loop** that turns one authenticated natural-language
request into a bounded sequence of registered-tool executions, using the LLM only to *decide the next
step*. This is the layer above M4 (model access) and M5 (tool execution); it is the first milestone
where the model can actually cause an effect — always through the M5 boundary, never around it.

Governing principle (from `AGENT_ARCHITECTURE.md`): **the model proposes; the backend disposes.**

**The mandatory invariant (must hold through M6+):**

```
User Request (authenticated)
  → AgentController (@AuthenticationPrincipal AuthenticatedUser)
  → AgentOrchestrator                      (new executionId, budgets, deadline)
  → AgentPlanner → LlmClient.generateStructured(prompt, AgentDecision.class)
  → AgentDecisionValidator                 (typed, cross-field validation; one bounded repair)
  → [FINAL] → AgentResult
  → [TOOL_CALL] → ToolExecutor.execute(name, args, backend-built ToolExecutionContext)
        → ToolRegistry.resolve → role authz → bind+validate input → Tool.execute
        → TaskService/CustomerService → AuthorizationService (ownership) → Repository
  → ToolResult → ObservationSerializer → bounded AgentObservation (fed back, delimited as untrusted)
  → loop (bounded by iterations / tool-calls / deadline / cancellation / loop-detection)
  → AgentResult → AgentExecuteResponse
```

**In scope:** `AgentDecision`/`AgentAction`; `AgentDecisionValidator`; `AgentObservation` +
`ObservationSerializer` (bounded); `AgentToolCatalog`/`AgentToolDefinition` (reflective adapter over
`ToolRegistry`); `AgentPromptService` (versioned template) + `resources/prompts/agent-system.st`;
`AgentPlanner` (decision + repair); `AgentOrchestrator` (the loop); `AgentExecution` (in-memory run
state); `CancellationToken`/`DeadlineCancellationToken`; `LoopDetector`; `AgentProperties` (env-tunable
bounds); `AgentResult`/`AgentStatus`; `agent.exception.*` integrated with `ApiException`;
`POST /api/v1/agent/execute` with request/response DTOs; unit + orchestrator (deterministic-fake) +
full integration (Testcontainers Postgres) tests; a `ScriptedLlmClient` test double; orchestration-level
Micrometer metrics; docs; ADR-0013…0016.

**Explicitly NOT in scope (later milestones — must not be built):**
- Redis conversation/session/execution memory; multi-turn/cross-request context (M7).
- Guardrails *enforcement engine*, **human-confirmation workflow**, **hard** per-tool timeout /
  thread interruption, rate limiting, production-grade side-effect controls, richer non-progress
  detection (M8).
- Durable audit tables (`agent_executions`, `agent_steps`, `tool_executions`) and
  `GET /api/agent/executions/{id}` retrieval (M9).
- Advanced prompt-injection guardrails beyond preserving the M4/M5 trust boundary (M8).
- Full evaluation harness / scored dataset tooling (later); Prometheus/Grafana dashboards (M10);
  Kafka; multi-agent / distributed orchestration; frontend.
- New side-effecting tools. M6 reuses the M5 registry as-is (`task.get`, `task.search`, `task.create`,
  `customer.get`, `customer.search`, `math.calculate`). No new `Tool` beans, no `task.update`/`delete`.

**Boundary guarantees (hard, test-enforced):** `com.prince.agentic.agent.*` may import `ai.llm.*`
(the LLM abstraction), `tool.*` (the execution boundary), `security.*`, and `common.*` — and **must
not** import any `*Repository`, `EntityManager`, `JdbcTemplate`, or a domain service
(`task.TaskService`, `customer.CustomerService`) directly. The orchestrator's only path to data or
effects is `ToolExecutor`. `agent.*` must not import a Spring AI (`org.springframework.ai.*`) type —
model access stays behind `LlmClient`.

---

## 2. Confirmed decisions (authoritative)

| # | Decision | Choice |
|---|---|---|
| D1 | Package | New feature package `com.prince.agentic.agent` (+ `agent.api`, `agent.api.dto`, `agent.exception`; test `agent.support`). Package-by-feature, mirrors `tool`. |
| D2 | Decision contract | `AgentDecision(AgentAction action, String response, String tool, Map<String,Object> arguments)`, `AgentAction ∈ {FINAL, TOOL_CALL}`. The LLM returns a **typed decision**, never free prose used as control protocol. |
| D3 | Decision production | Reuse M4: `AgentPlanner` calls `llm.generateStructured(renderedPrompt, AgentDecision.class)` once per iteration. **No new `LlmClient` method** — each iteration renders a fresh prompt carrying prior observations. |
| D4 | Decision validation | `AgentDecisionValidator` enforces cross-field rules (see §6). Malformed/parse failure **or** invalid combination → treated uniformly (like `AiService.attempt`) → **one** bounded repair re-ask → else `AGENT_INVALID_DECISION`. Exactly one repair; no second uncontrolled loop (aligns M4). |
| D5 | Tool invocation | Only through `ToolExecutor.execute(toolName, arguments, context)`. The orchestrator never resolves/instantiates a tool, never calls a domain service, never touches a repository. |
| D6 | Identity | `ToolExecutionContext` built by the backend from the `@AuthenticationPrincipal AuthenticatedUser`, carrying the run's **stable `executionId` and `requestId`** (via `new ToolExecutionContext(principal, requestId, executionId, meta)`) so every tool call correlates to the run. The model never supplies `userId`/`roles`/`executionId`. |
| D7 | Bounds (5, independent) | `maxIterations`, `maxToolCalls`, wall-clock `deadline`, `CancellationToken`, `LoopDetector`. All **cooperative** (checked between steps). No `while(true)`. See §11–§16. |
| D8 | Observations | `ToolResult` is **never** placed raw into the prompt. `ObservationSerializer` produces a bounded `AgentObservation` (JSON string capped by `maxObservationChars`, arrays capped by `maxArrayItems`). See §17–§18. |
| D9 | Tool catalog | `AgentToolCatalog` derives `AgentToolDefinition`s **reflectively from `ToolRegistry.descriptors()`** (name, description, category, risk, input field names + types + enum values). No hardcoded tool list; registry stays authoritative. No Spring AI schema engine. |
| D10 | Prompt | Versioned template `resources/prompts/agent-system.st` rendered by `AgentPromptService`. Untrusted text (user request, observations) is substituted only into delimited slots; instructions are fixed. Prompt edits are behaviour changes (§19). |
| D11 | Result | `AgentResult(executionId, AgentStatus status, String finalResponse, int iterations, int toolCalls, long durationMs, String failureCode)`. `AgentStatus ∈ {COMPLETED, FAILED, TIMED_OUT, CANCELLED, LIMIT_REACHED, LOOP_DETECTED}`. |
| D12 | API | `POST /api/v1/agent/execute`, authenticated (deny-by-default). Request `{message}`. Response 200 `AgentExecuteResponse` for **all orchestration outcomes** (incl. non-COMPLETED). Only request-DTO validation (400) and auth (401) use the error envelope. See §22–§23. |
| D13 | Retry policy | No generic retry loop in the orchestrator. Provider-transient retry stays in M4/`LlmClient`; decision-repair is the single bounded exception (D4). Side-effecting tools are **never** auto-retried (§25). |
| D14 | Config | `AgentProperties` (`@ConfigurationProperties("agent")`): `maxIterations=8`, `maxToolCalls=10`, `timeoutSeconds=60`, `loopThreshold=2`, `maxObservationChars=2000`, `maxArrayItems=20`. Env overrides `AGENT_*`. No magic constants in code. |
| D15 | Metrics | Orchestration-level only (`agent.execution.duration`, `agent.execution.count`, `agent.tool.calls`, `agent.iterations`, `agent.loop.detected`, `agent.limit.reached`). Never re-count M5's `tool.execution.*`. |
| D16 | State scope | `AgentExecution` is **in-memory, single-request**. No Redis, no DB. Each call = new `executionId`, new state (§29–§30). |
| D17 | Clock | Inject `java.time.Clock` for a deterministic, testable deadline; deadline computed **once** at start. |

---

## 3. Architecture & components

All under `com.prince.agentic.agent`.

| Component | Type | Responsibility | Depends on |
|---|---|---|---|
| `AgentAction` | enum | `FINAL`, `TOOL_CALL`. | — |
| `AgentDecision` | record | Typed model decision (§5). | — |
| `AgentDecisionValidator` | class | Cross-field validity (§6); returns a typed verdict. | — |
| `AgentToolDefinition` | record | Model-facing tool description (name, description, category, risk, fields). | — |
| `AgentToolCatalog` | `@Component` | Build definitions + render catalog text from `ToolRegistry`. | `ToolRegistry` |
| `AgentPromptService` | `@Service` | Render `agent-system.st` with catalog + delimited request + observations + constraints. | — |
| `AgentPlanner` | `@Service` | One decision step: render → `generateStructured(AgentDecision)` → validate → one repair. | `LlmClient`, `AgentPromptService`, `AgentDecisionValidator` |
| `AgentObservation` | record | Bounded, model-safe view of a tool outcome. | — |
| `ObservationSerializer` | `@Component` | `ToolResult<Object>` → bounded `AgentObservation`. | `ObjectMapper`, `AgentProperties` |
| `LoopDetector` | class | Fingerprint repeated tool calls (§16). | `AgentProperties` |
| `CancellationToken` | interface | `boolean isCancelled()`. | — |
| `DeadlineCancellationToken` | class | Cancels when `Clock` passes the deadline (unifies timeout as cancellation). | `Clock` |
| `AgentExecution` | class | Mutable per-run state: ids, deadline, counters, observations, loop state, token. | — |
| `AgentStatus` | enum | Terminal statuses (§22). | — |
| `AgentResult` | record | Structured run outcome (§22). | — |
| `AgentProperties` | `@ConfigurationProperties` | Env-tunable bounds (§14). | — |
| `AgentOrchestrator` | `@Service` | The bounded loop (§11); assembles context, calls planner, executes tools, feeds observations, produces `AgentResult`. | `AgentPlanner`, `ToolExecutor`, `AgentToolCatalog`, `ObservationSerializer`, `LoopDetector`, `AgentProperties`, `Clock`, `MeterRegistry` |
| `agent.api.AgentController` | `@RestController` | `POST /api/v1/agent/execute`; resolves principal; maps `AgentResult`→DTO. | `AgentOrchestrator` |
| `agent.api.dto.AgentExecuteRequest` | record | `{ @NotBlank @Size message }`. | — |
| `agent.api.dto.AgentExecuteResponse` | record | Wire response (§22). | — |
| `agent.exception.AgentException` (+ `AgentInvalidDecisionException`) | class | `ApiException` subtypes for the *thrown* faults (§23). | `ApiException` |

**Dependency direction (enforced by `ArchitectureBoundaryTest` extension):**
`agent → { ai.llm, tool, security, common }`. **Forbidden:** `agent → *Repository | EntityManager |
JdbcTemplate | task.TaskService | customer.CustomerService | org.springframework.ai.*`.

---

## 4. Agent action types (§5, §31–§33)

`AgentAction`: `FINAL`, `TOOL_CALL` — the minimum for a deterministic loop.

- **FINAL** — the agent has enough information (or none was needed) and answers directly. Enables the
  direct-answer path ("Hello" → `FINAL`) so no tool is invoked unnecessarily.
- **TOOL_CALL** — the agent names a registered tool and supplies arguments.

`ASK_CLARIFICATION`, `WAIT`, `HANDOFF` are documented as **future** capabilities (not built): they add
turn-taking/memory concerns that belong with M7. M6 stays small and deterministic.

---

## 5. Agent decision model

```java
public enum AgentAction { FINAL, TOOL_CALL }

public record AgentDecision(
        AgentAction action,
        String response,             // FINAL only
        String tool,                 // TOOL_CALL only
        Map<String, Object> arguments// TOOL_CALL only (may be empty; never null-required)
) {}
```

The record is the target type for `LlmClient.generateStructured` — Spring AI's `BeanOutputConverter`
derives the JSON format instruction from it (including the `AgentAction` enum's allowed values), inside
`OllamaLlmClient`, so no Spring AI type leaks into `agent`. `arguments` is a free-form object map,
matching the untrusted, tool-specific shape the M5 `ToolExecutor` re-binds and re-validates.

---

## 6. Decision validation (§6, §7, §34–§35)

`AgentDecisionValidator` rejects malformed combinations so an invalid decision cannot silently pass:

| `action` | `response` | `tool` | `arguments` | Verdict |
|---|---|---|---|---|
| `FINAL` | non-blank | absent/blank | absent/empty | **valid** |
| `FINAL` | blank/null | — | — | invalid (`response` required) |
| `FINAL` | — | present | — | invalid (`tool` must be absent) |
| `TOOL_CALL` | absent/blank | non-blank | any (null → `{}`) | **valid** |
| `TOOL_CALL` | present | — | — | invalid (`response` must be absent) |
| `TOOL_CALL` | — | blank/null | — | invalid (`tool` required) |
| `null`/unparseable | — | — | — | invalid (`action` required) |

The validator does **not** check that `tool` names a registered tool or that `arguments` fit its schema
— that is the `ToolExecutor`'s job (two-level validation, §7). It only guarantees the decision envelope
is internally coherent before the orchestrator acts on it.

**Two-level argument validation (§7):** decision-envelope validation here → then, on execution,
`ToolExecutor` binds `arguments` to the tool's `inputType` with a **strict** `ObjectMapper`
(unknown props rejected → a spoofed `ownerId`/`userId` becomes `TOOL_INVALID_INPUT`) and runs Bean
Validation. The agent layer never assumes the executor's validation makes the decision safe, and never
re-implements the executor's binding.

**Repair (D4):** the planner performs the `generateStructured` call inside a try that normalises a
thrown `LlmInvalidOutputException` to "no decision" (exactly as `AiService.attempt` does). If the parse
fails **or** the validator rejects the decision, the planner re-asks once with an appended repair
instruction; a second failure throws `AgentInvalidDecisionException`. Bounded to two model calls per
step. Provider/timeout/unavailable errors are **not** repaired — they propagate to the orchestrator's
fault handling (§23).

---

## 7. Execution context & identity (§9, §28)

`AgentExecution` (mutable, one per request) holds the authoritative, backend-created identity + budget:

```
AgentExecution
  executionId : String (UUID, generated by the backend)
  principal   : AuthenticatedUser (from @AuthenticationPrincipal — never from the request body/model)
  requestId   : String (correlation id)
  startedAt   : Instant (Clock.instant())
  deadline    : Instant (startedAt + timeoutSeconds — computed ONCE)
  maxIterations, maxToolCalls : int (snapshot of AgentProperties)
  iteration        : int (mutable)
  toolCallsUsed    : int (mutable)
  observations     : List<AgentObservation> (mutable, ordered)
  loopDetector     : LoopDetector (mutable fingerprint state)
  cancellation     : CancellationToken
  status           : AgentStatus (set at termination)
```

Per tool call the orchestrator builds a `ToolExecutionContext(principal, requestId, executionId, Map.of())`
so tool logs/metrics correlate to the run. The model can propose a tool + arguments; it can never make
`userId`/`roles`/`executionId` into security identity — tool inputs carry no identity field, and the
context's identity comes only from the verified principal. This is asserted by a dedicated test (§28).

---

## 8. Execution loop (§11)

```
result run(request, principal):
  ex = new AgentExecution(newId, principal, requestId, clock.now, deadline, props)
  loop:
    if ex.cancellation.isCancelled():            return terminate(ex, CANCELLED)
    if clock.now >= ex.deadline:                 return terminate(ex, TIMED_OUT)
    if ex.iteration >= ex.maxIterations:         return terminate(ex, LIMIT_REACHED, AGENT_ITERATION_LIMIT)
    ex.iteration++

    decision = planner.decide(request, catalog, ex.observations)   // may throw AgentInvalidDecisionException / LlmException

    if decision.action == FINAL:                 return terminate(ex, COMPLETED, response=decision.response)

    // TOOL_CALL
    if ex.toolCallsUsed >= ex.maxToolCalls:      return terminate(ex, LIMIT_REACHED, AGENT_TOOL_CALL_LIMIT)
    if loopDetector.isRepeat(decision.tool, decision.arguments):  return terminate(ex, LOOP_DETECTED, AGENT_LOOP_DETECTED)

    ctx = ToolExecutionContext(principal, requestId, ex.executionId, {})
    ToolResult r = toolExecutor.execute(decision.tool, decision.arguments, ctx)   // returns, never throws for run-path
    ex.toolCallsUsed++
    ex.observations.add(observationSerializer.toObservation(r))                    // success AND failure become observations
    // continue
```

`planner.decide` throwing `AgentInvalidDecisionException` or an `LlmException` is caught by the
orchestrator and converted to a terminal `AgentResult` (§23) — every started run returns a structured
result; the request never crashes. There is **no** unbounded path: `maxIterations` alone guarantees
termination; the other four bounds are independent early stops.

---

## 9. Tool call budget vs iterations (§12–§13)

`maxIterations` (LLM decision steps) and `maxToolCalls` (tool executions) are tracked **separately**
even though a step yields at most one tool call in M6. Keeping them distinct: (a) documents intent for
future actions that may not map 1:1 to a tool call, and (b) lets a run stop on tool budget while still
allowing a final `FINAL` step within the iteration budget. Defaults `8` / `10` (tool-call default
matches `GUARDRAILS.md`).

---

## 10. Time budget & cancellation (§14–§15)

- **Deadline** computed once (`startedAt + timeoutSeconds`); shared by the whole run — never reset per
  tool call. Checked between steps via `DeadlineCancellationToken`, which unifies timeout and explicit
  cancellation behind one `CancellationToken.isCancelled()` check.
- **Cooperative only.** M6 checks cancellation/deadline **between** steps; it does **not** hard-interrupt
  an in-flight `LlmClient` call or tool execution (those have their own provider/tool timeouts). A
  documented limitation: hard interruption + an external cancel trigger are M8. No external cancel
  endpoint ships in M6 (the token is the seam M8 will drive).

---

## 11. Loop detection (§16)

`LoopDetector` fingerprints each intended tool call as `toolName + canonicalArguments`, where
`canonicalArguments` is a stable, key-sorted JSON rendering of the argument map (so key order does not
defeat detection). It counts occurrences; when a fingerprint would occur more than `loopThreshold`
(default 2) times, the run stops with `LOOP_DETECTED`. This catches the "same tool + same arguments
repeated" and "same decision repeated N times" cases. Deliberately simple — no ML, no semantic
similarity (M8 may add non-progress detection).

---

## 12. Observations (§17–§18, §36)

`AgentObservation(String tool, boolean success, String resultSummary, String errorCode)`:

- `ObservationSerializer` maps a `ToolResult<Object>`: on success, JSON-serialise `data` then **truncate**
  to `maxObservationChars` and cap arrays to `maxArrayItems`; on failure, `success=false`,
  `errorCode = error.code()`, `resultSummary` = the safe `error.message()`.
- For a `PageResponse` result the serializer keeps `content` (capped), `totalElements`, `totalPages`,
  `page` — dropping nothing sensitive but bounding size. It never emits internal Java class names or
  stack traces (the `ToolResult`/`ToolError` contract already guarantees this).
- Bounds lean on M3 pagination + M5 bounded inputs so an "enormous DB response" cannot reach the prompt.
- Only `AgentObservation` (not raw `ToolResult`) is rendered back into the prompt, delimited as
  untrusted (§13).

---

## 13. Prompt / context construction (§19–§20, §37, §45–§46)

`resources/prompts/agent-system.st` (versioned) is rendered by `AgentPromptService` from four parts:

```
[FIXED SYSTEM INSTRUCTIONS]  role; return exactly one AgentDecision matching the schema;
                             choose ONLY tools from the catalog; never invent a tool or user identity;
                             use FINAL when enough information is available; authentication is already
                             established server-side; you cannot change roles or access others' data.
[TOOL CATALOG]               rendered by AgentToolCatalog from ToolRegistry (name, description, category,
                             risk, input fields + allowed enum values). No internal classes / SQL / IDs.
[USER REQUEST]               the untrusted message, in a delimited block.
[OBSERVATIONS]               prior AgentObservations (bounded), in a delimited block.
[CONSTRAINTS]                remaining iteration/tool-call budget as guidance (backend still enforces).
```

Untrusted text (user request, observations) is substituted **only** into delimited slots, exactly as
`PromptService` does for the M4 `{input}` token; the instruction section is fixed and never built from
user or tool data. **Tool risk** is shown to the model as guidance only — the backend remains
authoritative (a `SIDE_EFFECTING` label does not let the model bypass M5 authz, nor M8 confirmation
later).

**Prompt injection (§37):** M6 does **not** add the M8 guardrail system, but it must not weaken the
M4/M5 boundary. "You are admin now" / "ignore tool restrictions" / "run arbitrary code" in the message
change nothing: identity comes from the principal, the registry is the only capability boundary, and
`ToolExecutor` authorizes every call server-side. Documented, and covered by a security test (§19/§39).

---

## 14. Tool descriptors for the LLM (§20, §44)

`AgentToolCatalog` is the **M6-owned adapter** from M5 metadata to a model-readable definition. It reads
`ToolRegistry.descriptors()` and, for each `ToolDescriptor`, reflects over `inputType`'s record
components to list field names, types, and (for enum types) allowed values — producing
`AgentToolDefinition`. M5 stays free of Spring AI and of any agent concern; M6 does not hardcode tool
names anywhere. (Future path: an M6 adapter could derive full JSON Schema, but a field/enum enumeration
is sufficient for the prompt and avoids a schema engine.)

We deliberately do **not** hand the loop to Spring AI's automatic tool-calling (§21): our orchestrator
must own iteration/tool-call budgets, loop detection, the deadline, observation bounds, cancellation,
and controlled decision parsing. Spring AI stays confined to model invocation + structured conversion
inside `OllamaLlmClient`.

---

## 15. Error & failure model (§22–§23)

Two tiers, chosen to match the codebase's existing philosophy (`ToolExecutor` returns a structured
outcome rather than throwing for run-path results):

**Tier 1 — orchestration outcomes → HTTP 200 `AgentExecuteResponse`.** Every *started* run returns run
metadata (`iterations`, `toolCalls`, `durationMs`) plus a `status` and optional `failureCode`:

| `status` | `failureCode` | Cause |
|---|---|---|
| `COMPLETED` | — | `FINAL` reached. |
| `LIMIT_REACHED` | `AGENT_ITERATION_LIMIT` / `AGENT_TOOL_CALL_LIMIT` | Budget exhausted; partial summary labeled incomplete. |
| `LOOP_DETECTED` | `AGENT_LOOP_DETECTED` | Repeated fingerprint. |
| `TIMED_OUT` | `AGENT_TIMEOUT` | Deadline passed between steps. |
| `CANCELLED` | `AGENT_CANCELLED` | Cancellation observed. |
| `FAILED` | `AGENT_INVALID_DECISION` / `AGENT_LLM_ERROR` / `AGENT_EXECUTION_FAILED` | Decision unrepairable, provider fault, or unexpected error mid-run. |

Rationale: budget/loop/timeout ends are *normal, bounded* states (`GUARDRAILS.md` §3: "partial result
clearly labeled incomplete"), and even faults produce useful run metadata an `ApiError` envelope cannot
carry. So they are reported in the response body with a stable `failureCode`, satisfying brief §23's
"stable errors" while integrating cleanly with the existing API (see §20 R3).

**Tier 2 — pre-run faults → standard `ApiError` envelope.** Request-DTO validation (`400
VALIDATION_ERROR`) and authentication (`401`) are handled by the existing `GlobalExceptionHandler` /
security entry point, unchanged. `AgentInvalidDecisionException` extends `ApiException` (422
`AGENT_INVALID_DECISION`) and is available for any caller that invokes the planner outside the loop, but
inside the loop the orchestrator catches it into Tier 1 `FAILED`.

`AGENT_TOOL_NOT_FOUND` / `AGENT_TOOL_ARGUMENT_ERROR`: these surface as **observations** (from the
`ToolResult`'s `TOOL_NOT_FOUND` / `TOOL_INVALID_INPUT`), not as run-terminating errors — the agent may
recover on the next step. They are listed as recognised codes for completeness but are not distinct
terminal statuses.

---

## 16. Retry & side-effect safety (§24–§26)

- **No generic retry loop** in the orchestrator. Provider-transient retry belongs to M4/`LlmClient`;
  tool-transient semantics belong to M5/M8. The only agent-level "retry" is the single bounded
  decision repair (D4).
- **Side-effecting tools are never auto-retried.** If `task.create` executes and the next LLM decision
  is invalid, the orchestrator repairs the *decision*, it does not re-run the tool. The exact M5
  `ToolResult` is preserved as the observation. Tool execution is **not** automatically idempotent; M8
  adds confirmation and stronger side-effect controls. Documented in `TOOL_SYSTEM.md` / `GUARDRAILS.md`.
- **Tool safety (§26):** the registry decides existence, the executor decides role-authorization, the
  domain service decides ownership, Bean Validation decides input correctness. The model overrides none
  of these.

---

## 17. API contract (§27–§30)

```
POST /api/v1/agent/execute            (authenticated; deny-by-default; @SecurityRequirement bearerAuth)

Request:  { "message": "Show me my high-priority tasks" }     // @NotBlank, @Size(max=...)
Response 200:
{
  "executionId": "b1f0...",
  "status": "COMPLETED",
  "response": "You have 3 high-priority tasks: ...",
  "iterations": 2,
  "toolCalls": 1,
  "durationMs": 842,
  "failureCode": null          // present (non-null) only for non-COMPLETED
}
```

- The request DTO carries **only `message`** — no `userId`/`role`/`ownerId`. Unknown JSON properties are
  ignored by default binding; a test asserts an injected identity field cannot influence the run (§28).
- Request-scoped: each call = fresh `executionId`/state; no cross-request memory (§29–§30). M6 is
  **single-request, single-execution** orchestration.
- No `/agent/admin` route. No execution-retrieval endpoint (that is M9, needs durable audit).
- Documented in SpringDoc/Swagger and `docs/API.md`.

---

## 18. Observability (§38, §40)

Micrometer, orchestration-level only (M5 already records `tool.execution.*`; we do not double count):

| Metric | Type | Tags |
|---|---|---|
| `agent.execution.duration` | timer | `status` |
| `agent.execution.count` | counter | `status` |
| `agent.tool.calls` | counter | — (incremented per executed tool call) |
| `agent.iterations` | distribution summary | — |
| `agent.loop.detected` | counter | — |
| `agent.limit.reached` | counter | `limit` (`iteration`/`tool_call`) |

Structured SLF4J logging carries `executionId` + `requestId`; log decision **action** and chosen tool
name, never full prompts, full arguments, or full observations (redaction per `DATA_PRIVACY.md`).
Performance (§40) is **measured, not claimed** — the agent is slower than a plain endpoint; we report
`durationMs` and never assert an unmeasured number.

---

## 19. Testing strategy (§41–§43)

A new `agent.support.ScriptedLlmClient` (implements `LlmClient`) dequeues a **sequence** of structured
decisions across iterations and can be set to a failure mode — the payoff of the `LlmClient` abstraction
for multi-step agent tests (the M4 `FakeLlmClient` returns a single value, so a sequence-aware double is
added rather than disturbing M4 tests).

**Unit** (no Spring, no network): `AgentDecisionValidator` (every row of §6); `LoopDetector`
(repeat/threshold, key-order-insensitive); `AgentProperties`/budget logic; `ObservationSerializer`
(truncation, array cap, failure mapping, `PageResponse` shaping); `AgentToolCatalog` (definitions from a
fake registry, enum enumeration); `DeadlineCancellationToken` (with a fixed/adjustable `Clock`);
`AgentExecution` counters.

**Orchestrator** (real `AgentOrchestrator` + `ScriptedLlmClient` + real `ToolExecutor`/registry with fake
or mocked domain services): direct `FINAL`; single tool call → `FINAL`; multi tool call; invalid decision
→ repair success; invalid decision → repair fail → `FAILED`; tool-not-found observation → recover;
tool unauthorized/forbidden observation; tool failure observation; `maxIterations`; `maxToolCalls`;
loop detection; timeout (advanced `Clock`); cancellation; **side-effect retry safety** (`task.create`
executes once even when the following decision is invalid).

**Full integration** (`@SpringBootTest` + Testcontainers Postgres; `ScriptedLlmClient` as `@Primary`
`@TestConfiguration` bean, mirroring `AiIntegrationTest`): authenticated USER A → `POST
/api/v1/agent/execute` → real orchestrator → real `ToolExecutor` → real `TaskService` → real DB, driving
`task.search` (HIGH priority) then `FINAL`; assert only USER A's tasks appear. USER A asks for USER B's
task id via `task.get` → service authorization → 404-masked → observation shows `NOT_FOUND`, agent
reports safely. Unregistered tool name (`database.dropAll`) → `TOOL_NOT_FOUND` observation, **no
execution**. Identity-spoof test (§28). All bounds have a test that trips them (`GUARDRAILS.md` §6:
"no code path that allows unbounded execution").

**Live Ollama** (§41): an optional, separately-tagged agent IT may exist but **must not** make
`./mvnw verify` depend on Ollama.

Coverage: keep the JaCoCo `BUNDLE ≥ 0.75` gate; exclude `agent/api/**` and DTO/record boilerplate
consistent with M5. No coverage-padding / "no exception thrown" tests.

---

## 20. Reconciliations (doc edits made in the same change)

| # | Conflict | Resolution (doc edit) |
|---|---|---|
| R1 | `ROADMAP.md` M6 output names `POST /api/agent/chat`; brief + `/api/v1/ai` convention say `/api/v1/agent/execute`. | Adopt **`POST /api/v1/agent/execute`**. Update `ROADMAP.md` M6 outputs and `API.md`. → ADR-0015. |
| R2 | `GUARDRAILS.md` §2 / `ROADMAP.md` assign loop-detection, execution-timeout, and max-tool-calls to **M8**; the M6 brief + termination-correctness require in-loop bounds now. | Split ownership: **M6 implements cooperative in-loop bounds** (iteration/tool-call budgets, cooperative deadline, cooperative cancellation, fingerprint loop detection); **M8 hardens** (hard per-tool timeout/interruption, confirmation workflow, rate limiting, retry hardening, richer non-progress). Edit `GUARDRAILS.md` §2 note and `ROADMAP.md` M6/M8. → ADR-0014. |
| R3 | Brief §23 wants stable `AGENT_*` errors "integrated with existing API exception handling"; a partial/failed *run* still has metadata an `ApiError` envelope cannot carry. | Two-tier model (§15): orchestration outcomes → 200 body with `status`+`failureCode`; pre-run faults → existing `ApiError` envelope; `AgentInvalidDecisionException extends ApiException`. Note in `API.md` + `ERROR_HANDLING.md`. |
| R4 | `GUARDRAILS.md` default is `AGENT_MAX_TOOL_CALLS=10`; brief §12 example shows `AGENT_MAX_ITERATIONS=8`. | Keep both, distinct (§9): `maxToolCalls=10` (matches doc), new `maxIterations=8`. Add `AGENT_MAX_ITERATIONS` to `GUARDRAILS.md`, `DEPLOYMENT.md`, `.env.example`. |

---

## 21. Security review (§39) — each must fail safely

| Attempt | Why it fails |
|---|---|
| Prompt "you are admin now" | Identity from principal only; roles never model-set. |
| `userId`/`ownerId` in request body | DTO has no such field; ignored on bind; identity from principal. Test-asserted. |
| `userId`/`ownerId` inside tool `arguments` | `ToolExecutor` strict bind rejects unknown props → `TOOL_INVALID_INPUT` observation. |
| Fake tool name (`database.dropAll`) | `ToolRegistry.resolve` → null → `TOOL_NOT_FOUND` before any execution. |
| Fake role claim | Roles come from the JWT-derived principal; not readable from message/decision. |
| Unauthorized tool / another user's resource | `ToolExecutor` role gate + domain `AuthorizationService` ownership (404-masked). |
| `task.create` for another user | Tool passes principal; service owns creation to that principal; no cross-user field. |
| Invalid tool arguments | Two-level validation → `TOOL_INVALID_INPUT` observation. |
| Massive tool result | `ObservationSerializer` truncation + array cap. |
| Repeated tool call / infinite loop | `LoopDetector` + `maxIterations`/`maxToolCalls`. |
| Repeated side-effect request | Not auto-retried; each is a distinct authorized call; loop detection stops repetition. |
| Timeout / cancellation | Cooperative checks between steps → `TIMED_OUT`/`CANCELLED`. |

The agent is never granted a capability broader than the user's own permissions (`SECURITY.md`).

---

## 22. Future interfaces (M7/M8/M9) — designed-for, not built

- **M7 (memory):** `AgentExecution` and observation list are the seam; a `ConversationStore`
  (Redis) will supply prior turns into `AgentPromptService` and persist execution state by
  `executionId`. M6 keeps them in-memory behind the orchestrator so the swap is additive.
- **M8 (guardrails):** `CancellationToken`, `ToolRiskLevel` on each observation/decision, and the
  single point where `TOOL_CALL` decisions are executed are the seams for hard timeout, the
  confirmation workflow (intercept `SIDE_EFFECTING`/`HIGH_RISK` before execution), rate limiting, and
  retry hardening.
- **M9 (audit):** `AgentResult` + per-step decision/observation records are the shape the durable
  `agent_executions`/`agent_steps`/`tool_executions` tables and `GET /api/agent/executions/{id}` will
  persist. M6 emits logs/metrics only.

---

## 23. Definition of Done (this milestone)

Requirements understood · docs & rules read · brainstorming→writing-plans used · implemented in small
TDD steps · every decision typed-validated and every tool call authorized before effect · input
validated & pre-run errors routed through the global handler · security reviewed (§21) ·
logging/metrics considered, no secrets/prompts/args logged (§18) · tests added at all three levels and
`./mvnw verify` green · docs updated (`AGENT_ARCHITECTURE`, `GUARDRAILS`, `TOOL_SYSTEM`, `API` + Swagger,
`SECURITY`, `TESTING`, `OBSERVABILITY`, `PERFORMANCE`, `EVALUATION`, `MEMORY`, `AUDIT_LOGGING`,
`ROADMAP`, `TECH_STACK`, `CHANGELOG`, `README`, `backend/README`) with correct
IMPLEMENTED/PLANNED labels · ADR-0013…0016 recorded · `.env.example` updated · no secrets · diff
reviewed · trade-offs and the cooperative-vs-hard-bounds limitation stated.
