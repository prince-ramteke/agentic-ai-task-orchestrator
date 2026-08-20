# Rule: Architecture

Always-on structural constraints. See `docs/SYSTEM_ARCHITECTURE.md` and `docs/AGENT_ARCHITECTURE.md`.

## Always
- Keep the layered flow: **Controller → Service → Repository**. Business logic lives in services.
- Keep the **agent layer separate** from domain logic. Domain services must be fully usable without the agent; the agent orchestrates over them through registered tools.
- Package-by-feature under `com.prince.agentic.<feature>`. Each feature owns its controller, service, repository, DTOs, mapper, exceptions.
- Depend on abstractions at boundaries: LLM/embeddings via a provider interface; tools via the tool registry.
- Prefer the simplest design that meets the requirement. Introduce an abstraction only when there are ≥2 real implementations or a proven need.
- Record any significant or hard-to-reverse decision as an ADR (`docs/ADR/`).

## Never
- Never let a controller contain business logic or call a repository directly.
- Never give the LLM direct access to the database, filesystem, network, or arbitrary code execution.
- Never embed business logic inside prompt strings.
- Never add a microservice, queue, or new datastore without an ADR justifying it.
- Never create a god service or a "utils" dumping ground. Split by responsibility.
- Never silently contradict a `docs/*.md` decision — change the doc in the same commit or don't do it.

## Boundaries that must stay clean
- **API ↔ domain:** DTOs only, never entities.
- **Agent ↔ domain:** tools only, never raw repository/service calls from the orchestrator to bypass authorization.
- **App ↔ LLM:** provider abstraction only.
- **App ↔ memory:** Redis for ephemeral state, PostgreSQL for durable data — never swap their roles.

## Work that belongs here
Module boundaries, layering, dependency direction, agent/domain separation, ADRs, and structural refactors.

## Skills for this area
- **Auto-consult:** `engineering:system-design`. For an ADR-worthy tech choice, `engineering:architecture`.
- **Verify before done:** `engineering:code-review`, `superpowers:verification-before-completion`.
- **Ignore:** frontend/design and doc-format skills unless the change is specifically in that area.
