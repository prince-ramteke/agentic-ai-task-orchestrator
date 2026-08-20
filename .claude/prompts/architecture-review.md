# Prompt: Architecture review

Use when evaluating a structural change or a proposed design.

---

**Proposal:** <what's being changed / added>

## Checklist
1. **Fit with existing architecture.** Consistent with `docs/SYSTEM_ARCHITECTURE.md` and `docs/AGENT_ARCHITECTURE.md`? Does it respect the layered flow and the agent/domain separation?
2. **Boundaries.** Are the clean boundaries preserved — API↔domain (DTOs), agent↔domain (tools + authorization), app↔LLM (provider abstraction), Postgres↔Redis (durable vs ephemeral)?
3. **Simplicity.** Is this the simplest option that meets the requirement? Any premature abstraction, unneeded service, or new datastore/queue that isn't justified?
4. **Reversibility.** How hard is this to undo? If significant or hard-to-reverse, an ADR is required (`docs/ADR/`).
5. **Trade-offs.** Are the alternatives and their trade-offs stated explicitly? What does each option cost in complexity, coupling, latency, and testability?
6. **Cross-cutting impact.** Security, observability, audit, testing, evaluation, deployment — what does this change require in each?
7. **Extensibility.** Does it leave room for the roadmap's future capabilities without building them now?

## Output
A clear recommendation (not a survey), the trade-offs, whether an ADR is needed (and a draft if so), and the cross-cutting follow-ups. Use `engineering:architecture` for the ADR and `engineering:system-design` for the design.
