# Milestone 5 — Tool Registry & Tool Execution Framework — Design Specification

- **Date:** 2026-08-21
- **Milestone:** M5 — Tool Registry
- **Status:** Approved design (implementation not started)
- **Author/Deciders:** Prince (owner) + Claude
- **Uses:** M2 security (`AuthenticatedUser`, `AuthorizationService`, `RoleNames`, deny-by-default) · M3 domain (`TaskService`, `CustomerService`, their DTOs — reused, not modified) · M1 error model (`ApiException`/`GlobalExceptionHandler`/`ApiError`, `/api/v1`) · M4 (the AI layer stays independent — the tool framework must **not** import `ai.*`).

> Source of truth for the M5 implementation plan. Resolves every "decide" point in the milestone
> brief and aligns with `CLAUDE.md`, `.claude/rules/ai-agent.md`, `docs/TOOL_SYSTEM.md`,
> `docs/AGENT_ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/GUARDRAILS.md`, `docs/TESTING.md`,
> `docs/ROADMAP.md`. Where a doc and this spec disagree, this spec lists the doc edit required
> (see §20 Reconciliations) so nothing is left contradictory.

---

## 1. Objective & scope

Build the **deterministic tool infrastructure** the future M6 agent will use: a typed, validated,
authorized execution boundary around selected backend capabilities. The governing principle:

> An AI tool is a controlled capability exposed through a typed, validated, authorized execution
> boundary — **not** an arbitrary Java method.

The LLM (M6) will only ever be able to name a **registered** tool and supply **arguments**; it never
touches repositories, `EntityManager`, `JdbcTemplate`, DB connections, reflection, arbitrary methods,
shells, or HTTP clients. Identity and authorization are supplied by the backend, never by the model.

**In scope:** the `Tool<I,O>` abstraction; `ToolDescriptor` metadata; `ToolRiskLevel`;
`ToolExecutionContext`; `ToolResult<O>` envelope; `ToolRegistry` (fail-fast startup validation,
immutable, O(1) lookup); `ToolExecutor` (resolve → authorize → bind → validate → execute → wrap);
a tool exception model integrated with the existing `ApiException` envelope; six deterministic tools
(one class each); a safe calculator; an ADMIN read-only `GET /api/v1/tools`; unit/integration/registry/
security/contract tests; lightweight Micrometer hooks; docs; ADRs.

**Explicitly NOT in scope (later milestones — must not be built):** the agent loop / planner / ReAct
(M6); LLM-driven tool selection or **Spring AI tool-calling** (`@Tool`, `FunctionToolCallback`,
`ChatClient` tool registration) (M6); Redis / agent memory (M7); guardrails engine, human-confirmation
workflow, hard timeout/cancellation, loop detection (M8); durable agent/tool audit tables
(`tool_executions`, `agent_executions`, `agent_steps`) (M9); Prometheus/Grafana dashboards (M10);
Kafka; frontend. No autonomous external side effects. **The tool framework must not import
`com.prince.agentic.ai.*`.**

**Boundary guarantees (hard, test-enforced):** `com.prince.agentic.tool.*` must not import `ai.*`,
`EntityManager`, `JdbcTemplate`, or any `*Repository`. Domain tools reach data **only** through
`TaskService`/`CustomerService`.

---

## 2. Confirmed decisions (authoritative)

| # | Decision | Choice |
|---|---|---|
| D1 | Core abstraction | `Tool<I, O>` with `ToolDescriptor descriptor()` and `O execute(ToolExecutionContext context, I input)`. Strongly typed, tiny; no giant framework. |
| D2 | Result shape | **Executor returns `ToolResult<O>`**; the `Tool.execute` handler returns raw `O` and throws typed exceptions. The `ToolExecutor` wraps success/failure + duration into the envelope (best for the future agent's observations). |
| D3 | Discovery | **Plain Spring bean injection, no annotation.** Each tool is a `@Component` implementing `Tool`; `ToolRegistry` receives `List<Tool<?,?>>` and indexes by `descriptor().name()`. Metadata lives only in the descriptor (no `@AgentTool`). |
| D4 | Registry lifecycle | Built once at startup, **fail-fast** validation, then **immutable** (unmodifiable map). O(1) name lookup. Thread-safe by immutability. No runtime registration, no dynamic plugin loading. |
| D5 | Risk levels | `READ_ONLY`, `DETERMINISTIC`, `SIDE_EFFECTING`, `HIGH_RISK` — **aligned to `TOOL_SYSTEM.md` §4** (not the brief's `LOW_RISK`; see §20 R1). |
| D6 | Tool naming | **Dot-namespaced, stable, machine-friendly**: `task.get`, `task.search`, `task.create`, `customer.get`, `customer.search`, `math.calculate`. Java class names are never the external identity (see §20 R2). |
| D7 | Versioning | A single `int`/`String version` field on the descriptor (default `"1"`). **Not** encoded in the name (`task.get`, not `task.get.v1`). A compatibility/rename strategy is deferred until a real second version exists. |
| D8 | Authentication | Every tool declares `requiresAuthentication`. All six M5 tools require authentication. **Fail closed**: an anonymous context on an auth-required tool → `TOOL_UNAUTHORIZED`. |
| D9 | Two authorization layers | **Role (tool-type) authorization** — "may this user *use* this tool?" — enforced by the executor from `descriptor().requiredRoles()` with **any-of semantics** (like Spring's `hasAnyRole`: empty = any authenticated; otherwise the principal must hold **at least one** required role). Shared domain tools declare `{ROLE_USER, ROLE_ADMIN}` so a normal user *and* an admin both pass at this layer (this project's admin holds only `ROLE_ADMIN`, and REST domain routes likewise require only "authenticated"); a future admin-only tool would declare `{ROLE_ADMIN}`. **Resource authorization** — "may this user touch this *resource*?" — delegated to the domain service via the passed principal (reuses `AuthorizationService`; tools never re-implement ownership, so admin-any-by-id and 404-masking are preserved). Both layers apply; they are not collapsed. |
| D10 | Execution context | `ToolExecutionContext` wraps the **authenticated principal** (`AuthenticatedUser`: userId, email, roles) + `requestId`, `executionId`, `metadata`. **Constructed by the backend from the security layer — never from tool arguments.** The model cannot manufacture `userId`/`roles`. |
| D11 | Executor API | `ToolResult<Object> execute(String toolName, Map<String,Object> rawArguments, ToolExecutionContext context)` — the M6-facing shape. Order: resolve → auth (role + requiresAuth) → **bind** `rawArguments`→`I` (Jackson) → **validate** `I` (Bean Validation) → execute → wrap. Authorization strictly precedes execution. |
| D12 | Input typing | Every tool has a typed input record with Bean Validation; inputs are **bounded** (ids, enums, `page ≤ 100`, string filters ≤ length, expression ≤ 256). Model arguments are untrusted; validation is stricter than ordinary internal calls. |
| D13 | Input reuse | `task.create` reuses the existing **`TaskCreateRequest`** (already ownerless → mass-assignment-safe). Read/search tools define small M5-owned input records (`TaskGetInput`, `TaskSearchInput`, `CustomerGetInput`, `CustomerSearchInput`, `CalculatorInput`). |
| D14 | Output typing | Tools return existing safe DTOs — `TaskResponse`, `PageResponse<TaskSummaryResponse>`, `CustomerResponse`, `PageResponse<CustomerSummaryResponse>` — never JPA entities. Calculator returns an M5-owned `CalculationResult`. |
| D15 | Error model | `ToolException extends ApiException` + concrete types → stable codes `TOOL_NOT_FOUND` (404), `TOOL_INVALID_INPUT` (400), `TOOL_UNAUTHORIZED` (401), `TOOL_FORBIDDEN` (403), `TOOL_TIMEOUT` (504), `TOOL_EXECUTION_FAILED` (500), `TOOL_REGISTRATION_ERROR` (startup — fails boot, not an HTTP response). Inside `ToolResult` these appear as `ToolError{code,message}`; if a tool exception ever surfaces via HTTP it renders through the existing handler. No stack traces, no Java class names leaked. |
| D16 | Domain-error surfacing | When a domain service throws an `ApiException` (e.g. `TaskNotFoundException` → `NOT_FOUND`, `AccessDeniedException` → 403 `FORBIDDEN`), the executor surfaces it as a **`ToolResult` failure preserving that code/status** (a meaningful observation for the agent), not a generic 500. |
| D17 | Timeout | `descriptor().timeout()` (a `Duration`, **default 10s** for all M5 tools) is **declared metadata**; the executor **measures** wall-clock `durationMs` and logs/annotates overruns. **Hard interruption/cancellation is NOT performed in M5** (would break `@Transactional` thread-binding + thread-local `SecurityContext` for domain tools) — it is designed with guardrails in **M8**. Documented limitation. |
| D18 | Calculator safety | Recursive-descent parser over `+ - * / ( )` with unary minus and decimals, evaluated on `BigDecimal`; input ≤ 256 chars. **No** `ScriptEngine`, `Runtime.exec`, `ProcessBuilder`, `eval`, or reflection. Divide-by-zero and malformed/foreign characters → `TOOL_INVALID_INPUT`. Grammar documented. |
| D19 | Schema strategy | The descriptor exposes `Class<I> inputType()` / `Class<O> outputType()`. **M5 builds no JSON-schema engine.** M6's Spring AI adapter derives the JSON schema from `inputType` (Spring AI already generates schemas from a type). |
| D20 | Spring AI boundary | The tool framework **does not depend on Spring AI or `ai.*`**. M6 will add a thin adapter (`ToolDescriptor` → Spring AI tool definition; `ToolExecutor` behind a `FunctionToolCallback`). That adapter lives in M6, not here. |
| D21 | HTTP surface | Exactly one endpoint: **`GET /api/v1/tools`** — **`@PreAuthorize("hasRole('ADMIN')")`**, read-only, returns descriptor metadata DTOs only (name, description, category, version, risk, requiresAuthentication, requiredRoles, inputType/outputType **simple names**). No implementation class names, no secrets. **No tool-execution endpoint in M5** (that is the agent, M6). |
| D22 | No durable audit | No DB tables, no Flyway migration. Observability is logs + Micrometer only (`tool.execution.duration` timer, `tool.execution.result` counter tagged `tool`/`risk`/`outcome`; bounded cardinality). Durable audit is M9. |
| D23 | Package | `com.prince.agentic.tool` (package-by-feature), sub-packages `tool` (core), `tool.exception`, `tool.math`, `tool.task`, `tool.customer`, `tool.api`. Domain tools live under the tool subsystem (not inside `task`/`customer`) so the boundary is one scannable tree. |
| D24 | ADRs | **ADR-0011** (tool abstraction, registry, discovery, result envelope, schema-via-`Class`, Spring-AI-adapter boundary) and **ADR-0012** (tool authorization & execution-context security boundary). Risk taxonomy is already fixed by `TOOL_SYSTEM.md` (no separate ADR). |
| D25 | Coverage | Keep the JaCoCo `BUNDLE ≥ 0.75` gate. Core/domain tool logic is fully tested; exclude only `tool/api/**` (thin controller/DTOs) and any pure-config from coverage as per existing conventions. No coverage-padding tests. |

---

## 3. Package & file layout (package-by-feature)

```
backend/src/main/java/com/prince/agentic/tool/
  Tool.java                          # interface Tool<I,O> { ToolDescriptor descriptor(); O execute(ctx, I); }
  ToolDescriptor.java                # record: name, description, category, version, risk, requiresAuthentication,
                                     #         requiredRoles(Set<String>), inputType(Class<I>), outputType(Class<O>), timeout(Duration)
  ToolRiskLevel.java                 # enum READ_ONLY, DETERMINISTIC, SIDE_EFFECTING, HIGH_RISK
  ToolExecutionContext.java          # principal(AuthenticatedUser), requestId, executionId, metadata; static factory from principal
  ToolResult.java                    # record<O>: toolName, success, data(O), error(ToolError), durationMs; ok(...)/failure(...)
  ToolError.java                     # record(code, message)
  ToolRegistry.java                  # injects List<Tool<?,?>>; validates; immutable index; resolve/contains/list/descriptors
  ToolExecutor.java                  # execute(name, Map rawArgs, ctx) -> ToolResult; the gate pipeline
  exception/
    ToolException.java               # abstract extends ApiException
    ToolNotFoundException.java       # 404 TOOL_NOT_FOUND
    ToolInvalidInputException.java   # 400 TOOL_INVALID_INPUT (carries field messages)
    ToolUnauthorizedException.java   # 401 TOOL_UNAUTHORIZED
    ToolForbiddenException.java      # 403 TOOL_FORBIDDEN
    ToolTimeoutException.java        # 504 TOOL_TIMEOUT (reserved; enforcement M8)
    ToolExecutionFailedException.java# 500 TOOL_EXECUTION_FAILED
    ToolRegistrationException.java   # startup failure (RuntimeException; fails boot) — TOOL_REGISTRATION_ERROR
  math/
    CalculatorTool.java              # name math.calculate; DETERMINISTIC; requiresAuthentication=true (fail-closed; all M5 tools authenticated)
    ExpressionEvaluator.java         # recursive-descent, BigDecimal; package-private, unit-tested directly
    CalculatorInput.java             # record(@NotBlank @Size(max=256) String expression)
    CalculationResult.java           # record(String expression, BigDecimal result)
  task/
    TaskGetTool.java                 # task.get       READ_ONLY     -> TaskService.get
    TaskSearchTool.java              # task.search    READ_ONLY     -> TaskService.list
    TaskCreateTool.java              # task.create    SIDE_EFFECTING-> TaskService.create
    TaskGetInput.java                # record(@NotNull @Positive Long taskId)
    TaskSearchInput.java             # record(TaskStatus status, TaskPriority priority, LocalDate dueBefore, @Min0 Integer page, @Range size)
  customer/
    CustomerGetTool.java             # customer.get    READ_ONLY    -> CustomerService.get
    CustomerSearchTool.java          # customer.search READ_ONLY    -> CustomerService.list
    CustomerGetInput.java            # record(@NotNull @Positive Long customerId)
    CustomerSearchInput.java         # record(CustomerStatus status, @Size String search, Integer page, Integer size)
  api/
    ToolCatalogController.java       # GET /api/v1/tools  (@PreAuthorize ADMIN)
    dto/ToolDescriptorResponse.java  # metadata-only projection of ToolDescriptor

backend/src/test/java/com/prince/agentic/tool/
  ToolRegistryTest.java              # registration, duplicate-name rejection, lookup, list, startup validation
  ToolExecutorTest.java              # resolve, auth (role + requiresAuth), bind, validation, execute, error mapping, duration, domain-error surfacing
  ToolDescriptorTest.java            # metadata invariants
  AbstractToolContractTest.java      # reusable contract every Tool must satisfy (§ contract tests)
  math/ExpressionEvaluatorTest.java  # arithmetic, precedence, parentheses, decimals, div-by-zero, malformed, dangerous-input rejection
  math/CalculatorToolTest.java       # descriptor + execute + contract
  task/TaskGetToolTest.java, TaskSearchToolTest.java, TaskCreateToolTest.java   # mock TaskService; contract
  customer/CustomerGetToolTest.java, CustomerSearchToolTest.java               # mock CustomerService; contract
  ToolSecurityTest.java              # USER/ADMIN/ownership/spoofed-ownerId/unauthorized (via executor + real services on H2)
  ToolCatalogApiTest.java            # GET /api/v1/tools: 200 ADMIN, 403 USER, 401 anonymous; metadata only
  ArchitectureBoundaryTest additions # tool.* must not import ai.*, EntityManager, JdbcTemplate, *Repository
```

**Modified files:** none in production for M2/M3/M4 (reuse only). `pom.xml` JaCoCo excludes add
`com/prince/agentic/tool/api/**`. Docs per §19.

---

## 4. The core contracts

```java
public interface Tool<I, O> {
    ToolDescriptor descriptor();
    O execute(ToolExecutionContext context, I input);   // raw O; throws typed exceptions
}

public record ToolDescriptor(
        String name, String description, String category, String version,
        ToolRiskLevel risk, boolean requiresAuthentication, Set<String> requiredRoles,
        Class<?> inputType, Class<?> outputType, Duration timeout) { /* compact-ctor validation */ }

public record ToolExecutionContext(AuthenticatedUser principal, String requestId,
                                   String executionId, Map<String,Object> metadata) {
    public static ToolExecutionContext forPrincipal(AuthenticatedUser principal) { /* generates ids */ }
}

public record ToolResult<O>(String toolName, boolean success, O data, ToolError error, long durationMs) {
    public static <O> ToolResult<O> ok(String name, O data, long ms) { ... }
    public static <O> ToolResult<O> failure(String name, ToolError error, long ms) { ... }
}
public record ToolError(String code, String message) {}
```

`ToolExecutor.execute(String toolName, Map<String,Object> rawArguments, ToolExecutionContext context)`
returns `ToolResult<Object>`. Identity comes only from `context.principal()`; `rawArguments` never
carry identity. The executor casts the resolved `Tool<?,?>` to `Tool<Object,Object>` after binding
`rawArguments` to `descriptor.inputType()` — the single, contained unchecked cast.

---

## 5. Execution pipeline (order is non-negotiable)

```
execute(name, rawArgs, ctx)
  1. resolve      registry.resolve(name)          → else TOOL_NOT_FOUND (ToolResult failure)
  2. authenticate ctx.principal present if descriptor.requiresAuthentication → else TOOL_UNAUTHORIZED
  3. authorize    descriptor.requiredRoles ⊆ ctx.principal.roles → else TOOL_FORBIDDEN   (role/tool-type layer)
  4. bind         objectMapper.convertValue(rawArgs, descriptor.inputType) → else TOOL_INVALID_INPUT
  5. validate     validator.validate(input)        → violations → TOOL_INVALID_INPUT (field messages)
  6. execute      tool.execute(ctx, input)         → domain service enforces RESOURCE authorization (ownership)
                     ├─ ApiException (NOT_FOUND/FORBIDDEN/CONFLICT…) → ToolResult failure preserving code/status
                     └─ other RuntimeException      → TOOL_EXECUTION_FAILED
  7. wrap         ToolResult.ok(name, data, durationMs) + metrics + metadata-only log
```

Nothing executes before steps 1–5 pass. Resource ownership is **not** re-implemented here — it is
enforced inside the domain service by passing `ctx.principal()`.

---

## 6. The six tools

| Name | Risk | Input | Calls | Output |
|---|---|---|---|---|
| `task.get` | READ_ONLY | `TaskGetInput(taskId)` | `TaskService.get(principal, id)` | `TaskResponse` |
| `task.search` | READ_ONLY | `TaskSearchInput(status,priority,dueBefore,page,size)` | `TaskService.list(principal, …)` | `PageResponse<TaskSummaryResponse>` |
| `task.create` | SIDE_EFFECTING | `TaskCreateRequest` (reused, ownerless) | `TaskService.create(principal, req)` | `TaskResponse` |
| `customer.get` | READ_ONLY | `CustomerGetInput(customerId)` | `CustomerService.get(principal, id)` | `CustomerResponse` |
| `customer.search` | READ_ONLY | `CustomerSearchInput(status,search,page,size)` | `CustomerService.list(principal, …)` | `PageResponse<CustomerSummaryResponse>` |
| `math.calculate` | DETERMINISTIC | `CalculatorInput(expression)` | `ExpressionEvaluator` (pure) | `CalculationResult(expression,result)` |

`task.search`/`customer.search` sort is **fixed to the service default** (no model-supplied sort
expression — least privilege; the services already whitelist sort, but the tool doesn't expose it in
M5). `page`/`size` are bounded (`size ≤ 100`, `page ≥ 0`); the service clamps too (defense in depth).
Destructive/`update` tools are deliberately excluded until M8 adds confirmation/guardrails.

---

## 7. Registry & startup validation (fail-fast)

`ToolRegistry` receives `List<Tool<?,?>>` (Spring injects every `@Component Tool`). At construction it
validates and throws `ToolRegistrationException` (failing boot) on: duplicate `name`; null/blank
`name`/`description`; null `risk`; null/`ROLE_`-malformed `requiredRoles`; null `inputType`/`outputType`;
null `timeout`; null handler. On success it stores an **unmodifiable** `Map<String,Tool<?,?>>` →
O(1) `resolve(name)`, plus `contains`, `list()` (immutable descriptor view). No runtime mutation.

---

## 8. Security model (the foundational invariant)

- **Identity is backend-supplied.** `ToolExecutionContext` is built from the authenticated
  `AuthenticatedUser` (from Spring Security / `@AuthenticationPrincipal`). The model may propose
  `{"tool":"task.get","arguments":{"taskId":123}}` but can never make `{"userId":999,"role":"ADMIN"}`
  into security identity — arguments bind only to the typed input, which contains no identity field.
- **Two authorization layers, both explicit** (D9): role/tool-type in the executor; resource/ownership
  in the domain service. Neither is skipped; they are not collapsed.
- **Fail closed:** unknown tool → not executed; missing auth → `TOOL_UNAUTHORIZED`; missing role →
  `TOOL_FORBIDDEN`; invalid/oversized input → `TOOL_INVALID_INPUT` before any execution.
- **No arbitrary execution:** one class per capability; no `UniversalCrudTool`, no reflection over
  methods, no model-provided SQL/code/shell; calculator uses a restricted grammar, not `eval`.
- **Registry is trusted backend infrastructure**, immutable after boot — no registry poisoning, no
  duplicate-name shadowing (rejected at startup).
- **No leakage:** errors are stable codes + safe messages; the catalog endpoint exposes metadata only.
- Threats explicitly defended (SECURITY.md update): arbitrary method execution, tool impersonation,
  role spoofing, owner spoofing, unauthorized invocation, excessive input, arbitrary code execution,
  registry poisoning, duplicate shadowing, argument injection, privilege escalation.

---

## 9. Observability (lightweight)

Micrometer via the existing `MeterRegistry`: `tool.execution.duration` (timer) and
`tool.execution.result` (counter) tagged `tool`, `risk`, `outcome` (bounded cardinality). `ToolExecutor`
logs at INFO on completion with metadata only (`tool.exec tool=… risk=… outcome=… durationMs=… user=<id>`),
WARN on failure. **No** durable audit records, **no** dashboards (M9/M10). Never log tool arguments in
full or any secret/PII.

---

## 10. Spring AI future-adapter boundary (documented, not built)

```
M6:  Spring AI ChatClient → FunctionToolCallback(s) generated from ToolDescriptor
        → adapter.invoke(name, jsonArgs) → ToolExecutor.execute(name, map, ctx) → ToolResult → observation
```
The adapter (name→schema via `inputType`, JSON args→`Map`, `ToolResult`→observation) is **M6 code**.
M5 exposes exactly what that adapter needs (`ToolRegistry.list()` descriptors + `ToolExecutor.execute`)
and nothing more, so the deterministic framework stays reusable and Spring-AI-free.

---

## 11. Testing strategy

- **Registry:** registration, duplicate-name rejection (boot fails), lookup/contains/list, each
  startup-validation rule trips `ToolRegistrationException`.
- **Executor:** resolve miss → `TOOL_NOT_FOUND`; anon → `TOOL_UNAUTHORIZED`; missing role →
  `TOOL_FORBIDDEN`; bad/oversized args → `TOOL_INVALID_INPUT`; happy path → `ToolResult.ok` with
  `durationMs`; domain `ApiException` → failure preserving code; identity taken from context not args.
- **Descriptor:** metadata invariants; risk classification correct per tool.
- **Contract (`AbstractToolContractTest`):** every tool has a valid descriptor, typed input/output,
  declared auth policy + risk, and safe error behavior — subclassed per tool so new M6 tools inherit it.
- **Calculator:** precedence, parentheses, decimals, unary minus, whitespace; divide-by-zero,
  malformed, and dangerous input (letters, `;`, `import`, function calls) → `TOOL_INVALID_INPUT`.
- **Domain tools:** unit tests mock `TaskService`/`CustomerService` (assert the tool passes the
  context principal and maps results); the tool never touches a repository.
- **Security (`@SpringBootTest`, H2, real services):** USER sees own; ADMIN admin-any-by-id; non-owner
  USER → `NOT_FOUND` observation (404-masking preserved); a spoofed `ownerId` in args is ignored
  (input has no owner field / `task.create` reuses ownerless DTO); anonymous/role failures.
- **Catalog API:** `GET /api/v1/tools` → 200 ADMIN (metadata only, no class names), 403 USER, 401 anon.
- **Boundary:** `tool.*` imports no `ai.*`/`EntityManager`/`JdbcTemplate`/`*Repository`.
- **No new DB infra**: only `ToolSecurityTest`/catalog use `@SpringBootTest` (H2); the rest are fast
  and deterministic. Reuse M3 Testcontainers only if a domain-integration IT is genuinely added.
- **Gate:** `./mvnw clean test` + `./mvnw verify` green at 0.75; `tool/api/**` excluded.

---

## 12. Error codes (stable, via existing envelope)

`TOOL_NOT_FOUND` (404) · `TOOL_INVALID_INPUT` (400) · `TOOL_UNAUTHORIZED` (401) · `TOOL_FORBIDDEN`
(403) · `TOOL_TIMEOUT` (504, reserved for M8) · `TOOL_EXECUTION_FAILED` (500) ·
`TOOL_REGISTRATION_ERROR` (startup, fails boot). Domain codes (`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`,
`VALIDATION_ERROR`) pass through unchanged when a service raises them.

---

## 13. Performance & concurrency

Registry lookup is O(1) on an unmodifiable `HashMap`; validation cost is paid once at boot. The
registry is immutable → inherently thread-safe for the concurrent access M6 will bring. Binding
(Jackson `convertValue`) and Bean Validation are cheap relative to any DB call. No async, no pooling,
no speculative concurrency. No measured numbers claimed.

---

## 14. Non-goals (restated)

Agent loop/planner/ReAct; LLM tool selection; Spring AI tool-calling; Redis/memory; guardrails/
confirmation/hard-timeout/loop-detection; durable audit tables; dashboards; Kafka; frontend;
`update`/`delete` tools; any tool-execution HTTP endpoint.

---

## 15. Definition of Done (M5)

`Tool<I,O>` + descriptor + risk + context + result envelope + registry (fail-fast, immutable) +
executor (ordered gates) implemented; six tools registered and passing the shared contract test;
calculator safe and grammar-documented; identity provably backend-sourced; two-layer authorization
enforced and tested (role + ownership-via-service); domain 404-masking/admin-any-by-id preserved; AI
independence enforced by a boundary test; `GET /api/v1/tools` ADMIN-only, metadata-only; error codes
stable through the existing envelope; Micrometer hooks added; `./mvnw clean test` + `verify` green at
the 0.75 gate; docs + ADR-0011/0012 updated; no Spring AI dependency; no durable audit tables; nothing
committed/pushed.

---

## 16. Risks & limitations

- **Timeout is advisory in M5** (D17): duration measured, not hard-enforced; cancellation is M8. This
  is the single most important documented limitation.
- **Calculator scope**: intentionally minimal (`+ - * / ()`, decimals, unary minus). No functions,
  variables, or exponent in M5 — documented grammar; extend later if needed.
- **Search sort not model-controllable** in M5 (fixed default) — least privilege; can widen later.
- **Binding leniency**: unknown JSON properties are rejected (Jackson `FAIL_ON_UNKNOWN_PROPERTIES` for
  tool binding) so a spoofed `ownerId`/`userId` field is a `TOOL_INVALID_INPUT`, not silently ignored —
  making argument injection loud.

---

## 17. Domain tool authorization walkthrough (why ownership isn't re-implemented)

`TaskGetTool.execute(ctx, TaskGetInput)` calls `taskService.get(ctx.principal(), input.taskId())`.
`TaskService.get` → `loadAuthorized` → `AuthorizationService.canAccess`; a non-owner USER gets
`TaskNotFoundException` (404-masking); ADMIN gets admin-any-by-id. The tool adds **no** ownership
code — it only supplies the authenticated principal and maps the typed result/exception. REST and
tool paths therefore share one business-authorization implementation (SECURITY.md invariant).

---

## 18. Spec self-review checklist (to run before writing the plan)

Placeholders, internal consistency (risk names/tool names consistent throughout), scope (single
plan-sized), ambiguity (executor signature, envelope, identity source all pinned). §20 records the two
reconciliations so no doc is left contradicting this spec.

---

## 19. Documentation updates (truthful, labeled M5 IMPLEMENTED / M6 PLANNED)

`TOOL_SYSTEM.md` (make it describe the *implemented* framework; fix risk-enum names R1 and tool names
R2; add the execution pipeline + registry + envelope) · `AGENT_ARCHITECTURE.md` (the M5 framework now
exists below the future agent; the invariant §58) · `SECURITY.md` (tool threat model + the identity
invariant) · `GUARDRAILS.md` (risk metadata + timeout hooks exist; enforcement M8) · `EVALUATION.md`
(tool contract tests exist; agent tool-selection evaluation stays M6/M11) · `OBSERVABILITY.md`
(`tool.execution.*` metrics) · `API.md` (`GET /api/v1/tools` + `TOOL_*` error codes) · `TESTING.md`
(M5 test layers) · `PERFORMANCE.md` (O(1) registry, no numbers) · `ROADMAP.md` (M5 status) ·
`TECH_STACK.md` (no new deps) · `CHANGELOG.md` · `DEFINITION_OF_DONE.md` (unchanged; referenced) ·
`README.md` + `backend/README.md` (tools). New: `ADR/0011-*`, `ADR/0012-*`, `ADR/README.md` rows.

---

## 20. Reconciliations (contradictions found & resolved)

- **R1 — Risk enum names.** The brief §9 suggested `LOW_RISK`; `TOOL_SYSTEM.md` §4 already committed to
  *Read-only / Deterministic / Side-effecting / High-risk*. **Resolution:** adopt
  `READ_ONLY, DETERMINISTIC, SIDE_EFFECTING, HIGH_RISK` (calculate = `DETERMINISTIC`). Update
  `TOOL_SYSTEM.md` to name the enum values explicitly. No `LOW_RISK`.
- **R2 — Tool naming.** `TOOL_SYSTEM.md` §7 listed camelCase examples (`getTask`, `searchTasks`); the
  M5 brief mandates dot-namespaced names. **Resolution:** adopt `task.get`, `task.search`,
  `task.create`, `customer.get`, `customer.search`, `math.calculate`; update `TOOL_SYSTEM.md` §4/§7
  examples to the dot form. Java class names are never the external identity.
- **R3 — Evaluation scope.** `EVALUATION.md` describes agent tool-selection scoring. **Not a
  contradiction:** M5 provides *tool contract* tests only; agent tool-selection evaluation remains
  M6/M11. Note this in `EVALUATION.md` so the boundary is explicit.
- No conflict with M2 `AuthorizationService`, M3 services (reused as-is), M4 `LlmClient` (not imported),
  ownership/404-masking/admin-any-by-id (preserved), the error model (extended, not replaced), or the
  JaCoCo gate (held, narrow excludes).
