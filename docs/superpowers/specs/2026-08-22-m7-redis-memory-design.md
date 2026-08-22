# M7 — Redis Conversation & Execution Memory — Design Spec

**Status:** APPROVED (2026-08-22). Source of truth for the M7 implementation.
**Milestone:** 7 (Memory). **Prerequisite:** M6 (Agent Orchestration).
**Scope label:** M7 IMPLEMENTED · M8 GUARDRAILS PLANNED · M9 AUDIT PLANNED.

---

## 0. Purpose & core principle

M6 executes a single, stateless agent request. M7 adds **short-term, request-to-request
conversation memory** so a user can continue an agent conversation across HTTP calls while
preserving strict user isolation, bounded memory, expiration, and predictable failure behavior.

Redis is **infrastructure-level ephemeral memory**, never a source of truth. Domain truth
(Task/Customer) stays in PostgreSQL; durable agent audit is deferred to M9. Redis holds only the
bounded conversational representation the agent needs to reconstruct short-term context.

**Locked relationship**

```
AgentController → AgentConversationService → ConversationMemoryService → RedisConversationMemoryService → Redis
                                          → AgentOrchestrator (M6, Redis-free) → LLM / Tools
```

The M6 `AgentOrchestrator` **must remain Redis-free.** Its only path to effects is still the M5
`ToolExecutor`; it receives already-rendered history as an opaque `String` and never knows Redis exists.

---

## 1. Locked decisions

| Area | Decision |
|---|---|
| API | Lazy `conversationId` on existing `POST /api/v1/agent/execute` (absent → new conversation). Always returned in the response. `DELETE /api/v1/agent/conversations/{id}`. No list endpoint. |
| conversationId | Server-minted **UUIDv4** (unguessable, non-enumerable). Never an authorization claim. |
| Key | `conv:{userId}:{conversationId}` (aligns with existing `docs/MEMORY.md` §2). |
| Ownership | Stored `ownerUserId` must equal the authenticated principal's id. Mismatch / missing / expired → **404** (existence-masked, matching M3/M5). |
| Stored | `USER` message, `ASSISTANT` final response, bounded `TOOL` summaries. **`SYSTEM` never stored.** No entity graphs, no JWT/security context, no raw `ToolResult` internals. |
| Representation | Single **JSON blob** per conversation via Jackson. No Java native serialization. No class-name polymorphic storage. `schemaVersion = 1`. |
| TTL | **Sliding**, default 24h (`AGENT_MEMORY_TTL_SECONDS=86400`), refreshed on every interaction. |
| Storage bound | 50 messages / 12,000 chars — deterministic latest-message trim before persist. |
| Context bound | 12 messages / 6,000 chars — deterministic latest-message render into the prompt (the *second*, smaller boundary; the full history is never sent to the model). |
| Concurrency | Documented **last-write-wins** (one atomic write per turn from the loaded snapshot). |
| Failure | **Hybrid:** existing conversation + Redis down at load → **503**; new conversation + Redis down → **stateless degradation**, `memoryStatus=UNAVAILABLE`, `conversationId=null`. |
| Health | Keep Spring Boot's default Redis health indicator. |
| Roles | `USER`, `ASSISTANT`, `TOOL`. |

---

## 2. Module — `com.prince.agentic.memory` (Redis-isolated, no Spring AI, no tool access)

- `ConversationMemory` (record): `conversationId, ownerUserId, createdAt, lastActivityAt, schemaVersion, List<MemoryMessage> messages`.
- `MemoryMessage` (record): `role (MemoryRole), content, tool (nullable), sequence, timestamp`.
- `MemoryRole` (enum): `USER, ASSISTANT, TOOL`.
- `MemoryBounds` (final, no Spring): pure functions —
  - `trimForStorage(messages, maxMessages, maxChars)` → keep latest within both bounds.
  - `renderContext(messages, maxMessages, maxChars)` → delimited history string, latest within both bounds.
- `ConversationMemoryService` (interface): the abstraction M6-integration depends on.
- `RedisConversationMemoryService` (impl): `StringRedisTemplate` + `ObjectMapper` + `MemoryProperties` + `MeterRegistry`.
- `MemoryProperties` (`@ConfigurationProperties("agent.memory")`), `MemoryConfig` (`@EnableConfigurationProperties`).
- Exceptions: `ConversationNotFoundException` (→404), `MemoryUnavailableException` (→503), both extending `ApiException`.

**Constraints:** no `org.springframework.ai.*` import in this package; tools never reference it (verified by an architecture-boundary test).

### `ConversationMemoryService` contract

```java
ConversationMemory startOrLoad(AuthenticatedUser principal, String conversationId);
// conversationId == null  -> mint UUID, empty memory (NO Redis read).
// conversationId != null  -> Redis GET + ownership check.
//    missing/expired/owner-mismatch -> ConversationNotFoundException (404)
//    Redis down                     -> MemoryUnavailableException (503)

void append(AuthenticatedUser principal, ConversationMemory memory, List<MemoryMessage> newMessages);
// trim to storage bounds, refresh TTL, single SET EX write. Redis down -> MemoryUnavailableException.

void delete(AuthenticatedUser principal, String conversationId);
// ownership-checked DEL. missing -> ConversationNotFoundException (404). Redis down -> MemoryUnavailableException.

String renderContext(ConversationMemory memory);   // bounded prompt history via MemoryBounds
```

---

## 3. Redis-touch points & failure semantics (the fail-closed / best-effort split)

The ownership gate is **strictly fail-closed**; persistence is **best-effort** (per `MEMORY.md` §1
"losing it means convenience only"). The 503 for an existing conversation happens at **load, before any tool runs.**

| Path | Redis op | Redis down behavior |
|---|---|---|
| New conversation (no id) | none at load; `SET EX` at append | Load can't fail. Append fails → degrade: `memoryStatus=UNAVAILABLE`, `conversationId=null`. |
| Existing conversation (id) | `GET` at load | **503** — fail-closed, nothing executed, no ownership leak. |
| Existing conversation, up at load / down at append | `SET EX` at append | Turn already ran; return result with `memoryStatus=UNAVAILABLE` (append best-effort, logged + metric). |
| `DELETE /conversations/{id}` | `GET` + `DEL` | Redis down → 503. |

`memoryStatus` ∈ `{ ACTIVE, UNAVAILABLE }` (STATELESS reserved for a future explicit opt-out).

---

## 4. M6 integration & prompt change

Flow in `AgentConversationService.execute(principal, message, conversationId?)`:

1. `startOrLoad` (hybrid policy applied here: catch `MemoryUnavailableException` only for the *new* path → degrade; existing path propagates 503/404).
2. `renderContext` → bounded history string.
3. `AgentOrchestrator.run(principal, message, historyContext)` — the M6 loop, unchanged except it now
   forwards `historyContext` to the planner. **Never touches Redis.**
4. On completion, `append` `USER` message + `ASSISTANT` final response + bounded `TOOL` summaries
   (reusing M6's `ObservationSerializer` caps); TTL refreshed.
5. Return `AgentResult` + `conversationId` + `memoryStatus`.

**Prompt change (behavior change → re-run evaluation, per `.claude/rules/ai-agent.md`):**
add a `{history}` slot to `prompts/agent-system.st`, clearly delimited as untrusted conversational
context, placed **after** the system rules and **separate** from them — memory can never become a
replacement system prompt (§34, §33 memory-poisoning). `AgentPromptService.render(...)` and
`AgentPlanner.decide(...)` gain a `history` parameter; `AgentOrchestrator.run(principal, message)`
is kept (empty history) for backward compatibility and existing tests.

Identity always comes from Spring Security (`@AuthenticationPrincipal`), never from `conversationId`
or the request body. All M2–M6 boundaries preserved.

---

## 5. API (additive, non-breaking)

- `AgentExecuteRequest`: `{ message, conversationId? }` — `conversationId` optional, UUID `@Pattern`.
- `AgentExecuteResponse`: adds `conversationId` (always) and `memoryStatus`. Existing fields unchanged;
  old clients ignore the new fields (`@JsonInclude(NON_NULL)` retained).
- `DELETE /api/v1/agent/conversations/{id}` → `204` on success; `404` masked; authenticated; ownership-checked.
- Documented in `docs/API.md` + Swagger. Path/version unchanged.

---

## 6. Security & privacy

- Ownership: key embeds `userId` **and** stored `ownerUserId` is asserted == principal (defense-in-depth);
  mismatch → 404. LLM/client `conversationId` is never trusted for authorization.
- Untrusted memory text lives only in the delimited `{history}` slot, never in the instruction channel.
- No debug/get-key endpoint. No conversation listing. No raw message content in logs.
- Redis password via `REDIS_PASSWORD` in non-dev (`.env.example` placeholders); no secrets committed.
- Privacy (`docs/DATA_PRIVACY.md`): stored fields, purpose, sliding-TTL retention, user delete, log restrictions.

---

## 7. Observability

Lightweight Micrometer metrics only: `memory.load`, `memory.append`, `memory.trim`, `memory.hit`,
`memory.miss`, `memory.unavailable` (+ message-count / serialized-size summaries). Labels are
`memoryStatus`/outcome, never raw ids or content. Never log conversation text.

---

## 8. Testing

- **Unit:** `MemoryProperties` defaults; `MemoryBounds` trim-by-count & trim-by-chars & context render;
  serialization round-trip + malformed-data handling; ownership assertion; failure translation
  (mocked template throwing `RedisConnectionFailureException`); `FakeConversationMemoryService`.
- **Redis integration (Testcontainers `GenericContainer("redis:7-alpine")`, Docker-skip):** create, load,
  append, trim, TTL, expiration, delete, cross-user isolation (404), Redis-unavailable.
- **Agent multi-turn IT** (real `AgentConversationService` + real `AgentOrchestrator` + real Redis +
  `ScriptedLlmClient`): turn 1 "Show my high priority tasks." → turn 2 "Which one is due first?" —
  assert **turn 2's captured LLM prompt contains turn 1's bounded context** (via `ScriptedLlmClient.prompts()`),
  not merely that Redis holds messages.
- **Backward compatibility:** existing no-`conversationId` execution still valid (degrades cleanly without Redis).
- **Security:** User A cannot access User B's conversation; id manipulation cannot bypass ownership; expired → 404;
  key namespace correct; no sensitive content in logs.
- **Coverage gate held:** overall ≥ 75% (JaCoCo BUNDLE INSTRUCTION). `./mvnw clean test` then `./mvnw verify` green.
  Live Ollama tests stay profile-gated.

---

## 9. Contradictions found & resolution

1. `docs/MEMORY.md` §2 planned key `conv:{userId}:{conversationId}` — **adopted as-is**; promote planned → IMPLEMENTED and add `ownerUserId` value-assert.
2. `docs/MEMORY.md` §2 also lists `exec:{}`, `session:{}`, `cache:{}` — **M7 narrowed to conversation memory only;** those rows stay PLANNED.
3. `docs/API.md` agent response shape — additive fields only; documented in `API.md` + Swagger (additive change permitted by `.claude/rules/api.md`).

---

## 10. ADRs

- **ADR-0017** Redis Conversation Memory Architecture (store, key, module boundary).
- **ADR-0018** Memory Retention & Bounding (dual bounds, sliding TTL, last-write-wins).
- **ADR-0019** Redis Failure Semantics (hybrid fail-closed/degrade).
- **ADR-0020** Conversation Ownership & Isolation (server-minted UUID, ownership assert, 404-mask).

---

## 11. Out of scope (M7)

Durable agent audit (M9), hard guardrails/confirmation/rate-limiting (M8), exec/session/cache Redis
rows, semantic/vector memory, embeddings, RAG, conversation listing, tool→Redis access, Spring AI in the memory module.
