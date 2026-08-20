# Rule: Backend

Always-on constraints for Spring Boot code. Non-negotiable.

## Always
- Java 21 + Spring Boot 3.x. Constructor injection with `final` fields.
- Package-by-feature under `com.prince.agentic.<feature>`.
- Controller → Service → Repository. Business logic in services only.
- Return DTOs (records) from controllers; map entities → DTOs in a dedicated mapper.
- Validate every request DTO with Bean Validation (`@Valid`).
- Route all errors through the global `@RestControllerAdvice` handler (see `docs/ERROR_HANDLING.md`).
- `@Transactional` on writes; `readOnly = true` on reads.
- Access LLM/embeddings only via the provider abstraction; access tools only via the registry.
- Use `Optional` at repository boundaries, not threaded through deep logic.

## Never
- Never inject with field `@Autowired`.
- Never return or accept JPA entities at the API boundary.
- Never put business logic or repository calls in a controller.
- Never call an LLM SDK or a tool implementation directly from a feature outside its abstraction.
- Never hold a DB transaction open across a slow LLM or tool call — load data, commit, then call the model.
- Never swallow exceptions silently or leak stack traces to clients.

## When adding a feature package
Mirror an existing one: `controller`, `service`, `repository`, `dto/`, `mapper`, `exceptions`. Add tests in the parallel `src/test` path. Update Swagger and `docs/API.md`.

## Work that belongs here
Controllers, services, repositories, DTOs, mappers, validation, exception handling, transactions, and the wiring of the LLM/tool abstractions.

## Skills for this area
- **Auto-consult:** `engineering:system-design`. Also read `rules/api`, `rules/security`, `rules/database`, `rules/testing`, and (for agent-facing services) `rules/ai-agent`.
- **Verify before done:** `engineering:code-review`, `superpowers:verification-before-completion`.
- **Ignore:** frontend/design and doc-format skills.
