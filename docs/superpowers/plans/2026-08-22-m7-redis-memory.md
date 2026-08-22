# M7 — Redis Conversation Memory — Implementation Plan

**Approach:** inline TDD, task-by-task. Source of truth: the M7 design spec
(`docs/superpowers/specs/2026-08-22-m7-redis-memory-design.md`). No commit, no push, no branch switch.

Each task: **tests first → implement → files → verify → docs**. Run `./mvnw -o -q test` after each
code task; full `./mvnw verify` at the end.

---

## Task 1 — Dependency & configuration scaffolding
- **Tests first:** `MemoryPropertiesTest` — defaults applied when unset; positive values bound.
- **Implement:** add `spring-boot-starter-data-redis` to `pom.xml`; add `spring.data.redis.*`
  (host/port/password/timeout 2s) and `agent.memory.*` to `application.yml`; `MemoryProperties`
  (`@ConfigurationProperties("agent.memory")`, defaults 86400/50/12000/12/6000); `MemoryConfig`
  (`@EnableConfigurationProperties`).
- **Files:** `pom.xml`, `application.yml`, `memory/MemoryProperties.java`, `memory/MemoryConfig.java`,
  `test/.../memory/MemoryPropertiesTest.java`.
- **Verify:** `MemoryPropertiesTest` green; app context still starts.

## Task 2 — Memory model & bounds (pure)
- **Tests first:** `MemoryBoundsTest` — trim-by-message-count keeps latest N; trim-by-chars keeps
  latest within char budget; render produces delimited latest-N; empty → "(none)".
- **Implement:** `ConversationMemory`, `MemoryMessage` records; `MemoryRole` enum; `MemoryBounds`
  (static pure functions).
- **Files:** `memory/{ConversationMemory,MemoryMessage,MemoryRole,MemoryBounds}.java`,
  `test/.../memory/MemoryBoundsTest.java`.
- **Verify:** unit tests green.

## Task 3 — Memory service (interface, impl, fake, exceptions)
- **Tests first:** `RedisConversationMemoryServiceTest` (Mockito over `StringRedisTemplate`) —
  startOrLoad(null) mints UUID + empty, no Redis read; startOrLoad(existing) GET + ownership ok;
  owner-mismatch → `ConversationNotFoundException`; missing → 404; malformed JSON → 404 (treated as
  absent, logged); Redis exception → `MemoryUnavailableException`; append serializes + SET EX with TTL;
  delete ownership-checked. `FakeConversationMemoryServiceTest` sanity.
- **Implement:** `ConversationMemoryService` interface; `RedisConversationMemoryService`;
  `ConversationNotFoundException` (404 `CONVERSATION_NOT_FOUND`), `MemoryUnavailableException`
  (503 `MEMORY_UNAVAILABLE`); `test/.../memory/support/FakeConversationMemoryService`.
- **Files:** `memory/{ConversationMemoryService,RedisConversationMemoryService}.java`,
  `memory/exception/{ConversationNotFoundException,MemoryUnavailableException}.java`,
  `test/.../memory/support/FakeConversationMemoryService.java`, tests.
- **Verify:** unit tests green; `memory` architecture-boundary test (no Spring AI import).

## Task 4 — Redis integration tests (Testcontainers)
- **Implement:** `AbstractRedisIntegrationTest` (singleton `GenericContainer("redis:7-alpine")`,
  Docker-detect + `Assumptions.assumeTrue`, `@DynamicPropertySource` for `spring.data.redis.*`);
  `RedisConversationMemoryIT` — create/load/append/trim/**TTL sliding refresh**/**expiration**/delete/
  **cross-user 404 isolation**/Redis-unavailable behavior.
- **Files:** `test/.../support/AbstractRedisIntegrationTest.java`, `test/.../memory/RedisConversationMemoryIT.java`.
- **Verify:** runs for real (Docker present); TTL/expiry asserted with short override TTL.

## Task 5 — M6 integration seam (prompt + planner + orchestrator)
- **Tests first:** update `AgentPlannerTest`, `AgentOrchestratorTest`, `AgentControllerTest` for new
  signatures; add assertion that `{history}` renders into the prompt and is absent-safe.
- **Implement:** add `{history}` slot to `prompts/agent-system.st` (delimited, after rules);
  `AgentPromptService.render(userMessage, history, toolCatalog, observations, …)`;
  `AgentPlanner.decide(userMessage, history, observations, …)`;
  `AgentOrchestrator.run(principal, message, historyContext[, external])`; keep
  `run(principal, message)` delegating with empty history.
- **Files:** `resources/prompts/agent-system.st`, `agent/{AgentPromptService,AgentPlanner,AgentOrchestrator}.java`, tests.
- **Verify:** existing agent unit tests green with updated signatures.

## Task 6 — AgentConversationService (orchestration + hybrid policy + metrics)
- **Tests first:** `AgentConversationServiceTest` (fake memory + `ScriptedLlmClient` or mocked
  orchestrator) — new conversation returns minted id + ACTIVE; existing loads history and passes it to
  orchestrator; new + memory-unavailable → degrade (UNAVAILABLE, null id, result still returned);
  existing + unavailable-at-load → propagates 503; append writes USER+ASSISTANT(+TOOL) bounded messages.
- **Implement:** `AgentConversationService` (in `agent` package) with `ConversationOutcome`
  (result + conversationId + memoryStatus); `MemoryStatus` enum `{ACTIVE, UNAVAILABLE}`; metrics.
- **Files:** `agent/{AgentConversationService,MemoryStatus,ConversationOutcome}.java`, test.
- **Verify:** unit tests green.

## Task 7 — API surface (request/response/controller/delete)
- **Tests first:** update `AgentControllerTest` — execute returns conversationId + memoryStatus;
  DELETE 204 / 404 / 401; conversationId `@Pattern` rejects non-UUID → 400.
- **Implement:** `AgentExecuteRequest` (+`conversationId` UUID pattern); `AgentExecuteResponse`
  (+`conversationId`, `memoryStatus`); `AgentController` delegates to `AgentConversationService`,
  adds `DELETE /conversations/{id}`; Swagger annotations.
- **Files:** `agent/api/dto/{AgentExecuteRequest,AgentExecuteResponse}.java`, `agent/api/AgentController.java`, test.
- **Verify:** controller tests green.

## Task 8 — End-to-end integration tests
- **Implement:** `AgentConversationIT` extends `AbstractPostgresIntegrationTest` + adds a Redis container:
  - **Multi-turn:** turn 1 search → turn 2 follow-up; assert turn-2 prompt contains turn-1 context.
  - **Cross-user:** User B cannot use User A's conversationId → 404; no existence leak.
  - **Backward compat:** no-conversationId execute still COMPLETED.
  - **Delete:** create → use → delete → reuse returns 404.
- **Files:** `test/.../agent/AgentConversationIT.java` (+ shared Redis container helper).
- **Verify:** runs for real with Docker (Postgres + Redis).

## Task 9 — Docs, ADRs, env, changelog
- **Files:** `docs/MEMORY.md` (mark row IMPLEMENTED), `docs/AGENT_ARCHITECTURE.md`, `docs/API.md`,
  `docs/SECURITY.md`, `docs/DATA_PRIVACY.md`, `docs/OBSERVABILITY.md`, `docs/PERFORMANCE.md`,
  `docs/TESTING.md`, `docs/DEPLOYMENT.md`, `docs/ROADMAP.md`, `docs/TECH_STACK.md`, `docs/CHANGELOG.md`,
  `docs/GUARDRAILS.md`/`docs/AUDIT_LOGGING.md` (M8/M9 boundary notes), `.env.example`, `README.md`,
  `backend/README.md`, `docs/ADR/{0017,0018,0019,0020}-*.md`, `docs/ADR/README.md`.

## Task 10 — Verification
- `./mvnw -o clean test` then `./mvnw -o verify` (Docker present → Testcontainers Redis runs for real).
- Record: test totals, Redis IT results, coverage %, multi-turn proof, ownership/failure verification,
  files created/modified, ADRs, doc changes, issues fixed, limitations, `git status`. **STOP. No commit/push.**
