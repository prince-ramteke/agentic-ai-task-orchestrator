# M8 — Guardrails & Agent Safety Enforcement (Design Spec)

- **Status:** APPROVED — implementation in progress (M8)
- **Date:** 2026-08-22
- **Author:** prince-ramteke (with Claude)
- **Milestone:** M8 (builds on M2 auth, M3 domain, M4 LLM, M5 tools, M6 orchestration, M7 memory)
- **Boundary:** M9 owns durable audit. M10 owns observability dashboards. M8 adds *enforcement*, not persistence.

> M8 turns M6's cooperative, in-loop bounds into a centralized, backend-authoritative **policy
> enforcement layer** that evaluates every proposed agent action *before* any effect, and prevents
> unsafe behavior even when the LLM requests it. The LLM is never the final authority.

---

## 1. Objective

Build a deterministic guardrail layer that sits between the validated `AgentDecision` and the
`ToolExecutor`:

```
LLM decision → AgentDecision validation → GuardrailEngine → ALLOW / DENY / REQUIRE_CONFIRMATION
             → (rate-limit) → ToolExecutor → domain service → PostgreSQL
```

M8 **adds policy**. It must **not** duplicate M5's tool resolution, authentication, DTO binding,
Bean Validation, or resource-ownership authorization — those remain in `ToolExecutor` / domain
services and run exactly once.

## 2. Non-goals (explicit scope guard)

No durable audit tables (`agent_executions`, `tool_executions` — M9). No Kafka, no multi-agent, no
vector/RAG, no frontend, no external workflow engine, no distributed locking, no generic rules DSL,
no content-moderation platform, no Bucket4j / token-bucket machinery, no per-conversation rate
limiting. Policies are explicit Java + config. Confirmation state is short-lived Redis only.

## 3. Decision & outcome model

`GuardrailDecision` (immutable record):

| Field | Meaning |
|---|---|
| `outcome` | `ALLOW` \| `DENY` \| `REQUIRE_CONFIRMATION` |
| `reasonCode` | stable machine code (e.g. `GUARDRAIL_DENIED`, `CONFIRMATION_REQUIRED`, `UNSAFE_ACTION`, `POLICY_VIOLATION`) |
| `message` | short, safe, human-readable (no internals/prompts/secrets) |
| `riskLevel` | the tool's `ToolRiskLevel` (from the descriptor — never model-supplied) |
| `policyId` | which policy produced the outcome (for logs/metrics) |

Factory methods: `allow()`, `deny(code,msg,policyId)`, `requireConfirmation(risk,policyId)`. No other
outcomes are introduced.

## 4. Policy engine

`GuardrailEngine.evaluate(AuthenticatedUser principal, AgentDecision decision, GuardrailContext ctx)
→ GuardrailDecision`.

- `DefaultGuardrailEngine` resolves the `ToolDescriptor` from `ToolRegistry` (the **descriptor is
  authoritative**; the model cannot supply or downgrade risk). Unknown tool → `ALLOW` (deferred to
  `ToolExecutor`, which returns `TOOL_NOT_FOUND` as an observation, preserving M6 recovery).
- Runs an **ordered list of `GuardrailPolicy` beans**; the **first non-`ALLOW` wins**; otherwise
  `ALLOW`. Adding a policy = adding a bean, not editing the engine.
- `evaluate` is **pure** (no side effects) so it is fully deterministic and unit-testable.
- Emits `guardrail.{allow,deny,confirmation_required,policy_violation}` with low-cardinality labels
  (`tool`, `riskLevel`, `policyOutcome`) only.

**Policies (ordered):**

1. `ArgumentSafetyPolicy` (order 10) — structural argument checks *beyond* DTO validation: reject
   arguments whose serialized form exceeds `guardrail.max-argument-chars`, or that contain blatant
   control-injection markers. Breach → `DENY` `UNSAFE_ACTION`/`POLICY_VIOLATION`. This is a **modest,
   documented heuristic**, explicitly **not** a claim to solve prompt injection.
2. `RiskPolicy` (order 20) — `READ_ONLY`/`DETERMINISTIC` → `ALLOW`; `SIDE_EFFECTING`/`HIGH_RISK` →
   `REQUIRE_CONFIRMATION`. Deterministic, descriptor-driven.

`GuardrailContext` carries backend-controlled correlation only (`executionId`, `requestId`) — never
identity or model text as policy input.

## 5. Prompt / memory safety (structural, honest)

The real security boundary is **structural**: typed `AgentDecision` + `ToolRegistry` allowlist +
backend identity (`AuthenticatedUser` from the verified token) + authorization + `GuardrailEngine` +
confirmation + `ToolExecutor`. System instructions are backend-owned, immutable text. User messages,
conversation memory, and tool observations are **untrusted context** — already delimited by M6/M7 and
never treated as instructions or policy input.

M8 adds only a modest `ArgumentSafetyPolicy` and keeps the DTO's existing 4000-char message cap. We
**do not** claim regex/heuristics defeat prompt injection. This is proven by tests: a malicious memory
or user message cannot change role, permissions, tool risk, confirmation requirement, or identity,
because none of those derive from text — they derive from the descriptor and the verified principal.

## 6. Confirmation model

SIDE_EFFECTING and HIGH_RISK actions require explicit confirmation. On `REQUIRE_CONFIRMATION` the
agent run **halts** at `PENDING_CONFIRMATION`; the exact proposed action is stored, fingerprint-bound,
and executed **exactly once** only after the user confirms. **No automatic LLM-loop resume** — a later
user turn continues the conversation separately.

### 6.1 Action fingerprint (integrity)

`FingerprintService.fingerprint(userId, conversationId, toolName, canonicalArgs, riskLevel)` =
**SHA-256** hex over a canonical, sorted-key JSON of exactly those five bound fields. Changing *any*
bound field changes the fingerprint. Canonicalization uses a deterministic Jackson serialization
(sorted map keys).

### 6.2 Stored confirmation (`Confirmation`)

`{ id, ownerUserId, conversationId, toolName, canonicalArgsJson, riskLevel, fingerprint, createdAt,
expiresAt }`. Stored as one application-owned JSON blob in Redis under `guard:confirmation:{id}` with
TTL `AGENT_CONFIRMATION_TTL_SECONDS` (default 300). Separate namespace from `conv:{userId}:{...}` —
confirmation state is never mixed with conversation memory.

### 6.3 Confirm flow (`ConfirmationService`)

- `create(principal, conversationId, PendingAction)` → compute canonical args + fingerprint, store,
  return a safe `PendingConfirmation {confirmationId, tool, riskLevel, summary, expiresAt}`.
- `confirm(principal, id)` → **atomic single-use** consume via Redis `GETDEL` (`getAndDelete`), then:
  - not present → `CONFIRMATION_NOT_FOUND` (404);
  - `ownerUserId != principal.userId()` → masked `CONFIRMATION_NOT_FOUND` (never cross users);
  - `now ≥ expiresAt` (explicit, clock-checked) → `CONFIRMATION_EXPIRED` (410);
  - recomputed fingerprint ≠ stored fingerprint → `CONFIRMATION_MISMATCH` (409);
  - else return `ConfirmedAction {toolName, args, riskLevel}` (the **stored** action).
- `cancel(principal, id)` → owner-scoped delete.

The confirm endpoint accepts **no client arguments** — mutation is structurally impossible; the
stored action is what executes. Single-use is guaranteed by `GETDEL`: a replay or a concurrent second
confirm finds nothing → `CONFIRMATION_NOT_FOUND`/`ALREADY_USED`, so the action can execute **at most
once**.

### 6.4 Confirmed execution (`AgentConfirmationService`)

`confirm(principal, id)` → `ConfirmationService.confirm` → **rate-limit** → build a backend
`ToolExecutionContext` from the verified principal → `ToolExecutor.execute(stored tool, stored args,
ctx)` → map to a safe `AgentConfirmResponse`. The confirmed action runs through the *same*
`ToolExecutor` gates (auth, ownership, validation) — confirmation authorizes intent, never bypasses
authorization. Emits `guardrail.confirmation_approved`.

## 7. Rate limiting

`RateLimiter.tryAcquire(long userId) → boolean`. `RedisRateLimiter` uses a per-user **fixed window**:
key `guard:rate:{userId}:{epochMinute}`, `INCR` then `EXPIRE` (idempotent, ~2×window), compared to
`AGENT_USER_TOOL_BUDGET_PER_MIN` (default 60). `epochMinute` derives from an injected `Clock`
(testable window reset). Users are isolated by key. Over budget → the caller (orchestrator or confirm
service) stops with `RATE_LIMITED`; emits `guardrail.rate_limited`. No Bucket4j, no distributed token
bucket, no per-conversation limit.

Consumption happens **only on actual execution** (the `ALLOW` path in the loop, and the confirm path)
— never when an action is merely `REQUIRE_CONFIRMATION`.

## 8. Timeout strategy (layered; honest)

Three independent layers, none of which force-cancels a write:

1. **LLM-provider timeout** — existing `llm.request-timeout-seconds` on the provider call.
2. **Cooperative orchestration deadline** — existing M6 single wall-clock deadline, checked between
   steps (`AGENT_TIMEOUT_SECONDS`).
3. **Per-tool pre-execution budget check** — before invoking a tool, if the remaining deadline is
   already exhausted, fail *before* execution (`AGENT_TIMEOUT`) rather than start work that cannot be
   safely interrupted.

We **never** use `Future.cancel(true)` around a transactional / SIDE_EFFECTING domain operation. Once
a write has started it is allowed to finish; the system does not pretend it can be interrupted safely.

## 9. Retry hardening

No automatic retry of SIDE_EFFECTING or HIGH_RISK actions. A confirmation is single-use and is never
auto-reused after a failure (the user must re-initiate). The only automatic repair remains M6's single
bounded structured-output repair (decision level, no effect). Any counted retry counts against the
applicable budget.

## 10. Loop hardening

Preserve all M6 protections (max iterations, max tool calls, deadline, cancellation, fingerprint loop
detection). M8 adds no new planner. (Repeated-confirmation / no-progress detection is naturally
bounded by max-iterations and single-use confirmations; no extra machinery is added.)

## 11. Integration into the agent layer

- `AgentStatus` gains `PENDING_CONFIRMATION` and `BLOCKED`.
- `AgentResult` gains a nullable internal `PendingAction {tool, arguments, riskLevel}` (like
  `observations`, never exposed raw).
- `AgentOrchestrator` gains `GuardrailEngine` + `RateLimiter` deps. In the loop, after M6's tool-call
  limit and loop detection, and **before** `ToolExecutor.execute`:
  `evaluate` → `DENY` ⇒ terminate `BLOCKED` (reasonCode); `REQUIRE_CONFIRMATION` ⇒ terminate
  `PENDING_CONFIRMATION` with the `PendingAction`; `ALLOW` ⇒ `rateLimiter.tryAcquire`; false ⇒
  terminate `BLOCKED` `RATE_LIMITED`; true ⇒ execute (unchanged). The orchestrator still never touches
  Redis or a repository directly — it depends on the `GuardrailEngine` / `RateLimiter` abstractions.
- `AgentConversationService` — on `PENDING_CONFIRMATION`, creates a confirmation via
  `ConfirmationService.create(principal, memoryState.conversationId(), pendingAction)`, appends the
  user message (best-effort, so the conversation id persists for a later separate turn), and returns a
  `ConversationOutcome` carrying the `PendingConfirmation`. Identity always from the principal.

## 12. API

Base path unchanged (`/api/v1/agent`, authenticated, deny-by-default).

- `POST /execute` — unchanged request shape; response is **additive**. When guardrails halt it returns
  `status:"PENDING_CONFIRMATION"` plus `confirmationId`, `confirmationTool`, `confirmationRiskLevel`,
  `confirmationSummary`, `confirmationExpiresAt` (all null otherwise). `status:"BLOCKED"` with a
  `failureCode` for a denial/rate-limit. The published fields are never removed.
- `POST /confirmations/{id}` — confirm and execute the exact stored action once. No request body args.
  Returns `AgentConfirmResponse {confirmationId, tool, status, resultSummary}` (safe).
- `DELETE /confirmations/{id}` — cancel a pending confirmation (204).

## 13. Error model

`GuardrailException extends ApiException` with concrete subclasses mapping to the standard `ApiError`
envelope via the existing `GlobalExceptionHandler`:

| Exception | Status | Code |
|---|---|---|
| `ConfirmationNotFoundException` | 404 | `CONFIRMATION_NOT_FOUND` |
| `ConfirmationExpiredException` | 410 | `CONFIRMATION_EXPIRED` |
| `ConfirmationMismatchException` | 409 | `CONFIRMATION_MISMATCH` |
| `ConfirmationAlreadyUsedException` | 409 | `CONFIRMATION_ALREADY_USED` |
| `RateLimitedException` | 429 | `RATE_LIMITED` |
| `GuardrailDeniedException` | 403 | `GUARDRAIL_DENIED` |

`GUARDRAIL_DENIED` / `CONFIRMATION_REQUIRED` / `POLICY_VIOLATION` / `UNSAFE_ACTION` also appear as
agent `failureCode`s / reason codes on the `/execute` response. No internal policy detail leaks to
clients.

## 14. Observability

Metrics (low-cardinality labels only — never userId/conversationId/args/prompt):
`guardrail.allow`, `guardrail.deny`, `guardrail.confirmation_required`,
`guardrail.confirmation_approved`, `guardrail.confirmation_expired`, `guardrail.rate_limited`,
`guardrail.policy_violation`. Structured logs carry executionId/requestId; arguments are never logged
raw.

## 15. Configuration

`guardrail.*` (mirrors the M7 `agent.memory.*` pattern; zero/unset → default):

| Key | Env | Default |
|---|---|---|
| `guardrail.confirmation-ttl-seconds` | `AGENT_CONFIRMATION_TTL_SECONDS` | 300 |
| `guardrail.user-tool-budget-per-min` | `AGENT_USER_TOOL_BUDGET_PER_MIN` | 60 |
| `guardrail.max-argument-chars` | `AGENT_MAX_ARGUMENT_CHARS` | 4000 |

## 16. Testing strategy

- **GuardrailEngine (unit):** READ_ONLY/DETERMINISTIC → ALLOW; SIDE_EFFECTING/HIGH_RISK →
  REQUIRE_CONFIRMATION; unsafe args → DENY; unknown tool → ALLOW (deferred).
- **Fingerprint (unit):** stable for identical input; differs when any bound field changes.
- **Confirmation (unit + Redis IT):** exact action executes once; replay rejected; cross-user
  rejected; expired rejected; mismatch rejected; concurrent confirm executes at most once (GETDEL).
- **RateLimiter (Redis IT):** below allowed; at/over denied; new minute resets; users isolated.
- **Timeout (unit):** provider timeout propagates as failure; deadline-before-tool → TIMED_OUT with no
  execution; no forced interruption of a write.
- **Prompt/memory safety (unit):** malicious message/memory cannot change risk, confirmation, role, or
  identity; model cannot invent an admin identity (identity is the verified principal).
- **Orchestrator (unit):** side-effect tool → PENDING_CONFIRMATION (executor not called); DENY →
  BLOCKED; rate-limit → BLOCKED; read-only path unchanged.
- **End-to-end (IT):** execute → PENDING_CONFIRMATION → confirm → ToolExecutor → TaskService →
  PostgreSQL; task created **exactly once**; replay does not create a second.

Coverage gate stays at ≥0.75 bundle instruction ratio (not lowered). Live-Ollama tests remain
profile-gated. Testcontainers ITs skip cleanly without Docker.

## 17. Final security review checklist (verified before done)

replay · argument mutation · cross-user confirmation · risk downgrade · authorization bypass · memory
poisoning · rate-limit bypass · unsafe retry · transactional-cancellation assumptions · direct tool
execution bypassing the GuardrailEngine.

## 18. ADRs

- **ADR-0021** Guardrail Policy Engine (ordered pure policies, first-non-ALLOW-wins, no DSL).
- **ADR-0022** Side-Effect Confirmation Model (halt + execute-exact-once, no auto-resume).
- **ADR-0023** Layered Timeout Strategy (no forced cancel of writes).
- **ADR-0024** Confirmation Integrity / Action Fingerprinting (SHA-256 over 5 bound fields, GETDEL
  single-use).
- **ADR-0025** Per-User Fixed-Window Rate Limiting (Redis INCR/EXPIRE, no Bucket4j).
