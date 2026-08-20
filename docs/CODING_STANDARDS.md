# Coding Standards
## Agentic AI Task Orchestrator

Readable, production-quality Java over clever code. These standards make reviews fast and the codebase predictable.

## 1. Naming

- Classes: `XxxController`, `XxxService`, `XxxRepository`, `XxxRequest`/`XxxResponse`, `XxxMapper`, `XxxException`, `XxxTool`.
- Packages: `com.prince.agentic.<feature>` (feature-first).
- Methods: verbs; booleans read as predicates (`isOwner`, `hasRole`). Test methods: `method_condition_expected`.
- No abbreviations that aren't standard; no `Manager`/`Util` dumping grounds.

## 2. Package organization

Package-by-feature. Each feature package owns its `controller`, `service`, `repository`, `dto/`, `mapper`, `exceptions`. Cross-feature shared code lives in `common/`. The agent stack lives in dedicated packages (`agent`, `tool`, `llm`, `memory`, `audit`), kept separate from domain features.

## 3. Class responsibility

- One reason to change per class. Controllers = HTTP; services = business logic; repositories = persistence; mappers = entity↔DTO; tools = orchestrate a domain call behind the tool contract.
- Keep classes and methods small. If a service grows a god-like surface, split by responsibility.

## 4. DTOs & boundaries

- **Records** for DTOs. Never expose or accept JPA entities at the API boundary. Map in a mapper.
- Tool inputs/outputs are typed records too (`TOOL_SYSTEM.md`).

## 5. Dependency injection

- **Constructor injection with `final` fields.** No field `@Autowired`. This keeps classes testable and dependencies explicit.

## 6. Immutability

- Prefer immutable types (records, `final` fields, unmodifiable collections). Mutate only where there's a clear reason.

## 7. Validation

- Bean Validation on request DTOs (`@Valid`). Validate model-generated tool arguments explicitly inside tools. Fail fast on invalid input.

## 8. Exceptions

- Throw specific domain exceptions; map centrally (`ERROR_HANDLING.md`). Never swallow; never leak internals to clients.

## 9. Transactions

- `@Transactional` on writes, `readOnly = true` on reads. Keep transactions short. **Never** hold one across an LLM/tool call.

## 10. Logging

- SLF4J, structured, with `correlationId`/`executionId`. Right level (DEBUG/INFO/WARN/ERROR). No `System.out.println`. Never log secrets, tokens, full prompts, or full payloads (`OBSERVABILITY.md`, `DATA_PRIVACY.md`).

## 11. LLM & tool access

- Only through the provider abstraction and the tool registry. No vendor SDK calls or raw tool execution from feature code.

## 12. Comments & docs

- Comment *why*, not *what*. Delete stale comments. Public/agent-facing tool descriptions are accurate and current (they affect model behavior).

## 13. Simplicity

- Introduce an abstraction only with a real second implementation or a proven need. No speculative generality, no premature optimization (`PERFORMANCE.md`). Delete dead code.

## 14. Formatting

- Consistent formatting (project formatter/`.editorconfig` when added). Small, focused methods. Early returns over deep nesting.

## 15. Tests

- Every non-trivial change ships with tests (`TESTING.md`). Real assertions, deterministic, no live network/LLM.
