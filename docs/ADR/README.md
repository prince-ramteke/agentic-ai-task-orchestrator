# Architecture Decision Records (ADRs)
## Agentic AI Task Orchestrator

An ADR captures a **significant, hard-to-reverse** architectural decision: the context, the choice, the alternatives, and the consequences. ADRs make the "why" durable so future work doesn't relitigate or silently contradict it.

## When to write an ADR

Write one for: choosing/replacing a core technology; the agent/orchestration model; the tool authorization model; the confirmation policy; the datastore split; the LLM provider strategy; anything expensive to undo or that constrains future work.

**Don't** write one for routine CRUD, naming, formatting, or a trivially reversible choice.

## Format

Copy this template to `docs/ADR/NNNN-short-title.md` (zero-padded, incrementing):

```markdown
# ADR-NNNN: <short title>

- **Status:** Proposed | Accepted | Superseded by ADR-XXXX | Deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** <who>

## Context
What problem/force requires a decision? Constraints, requirements, and the relevant docs.

## Decision
The choice made, stated plainly.

## Alternatives considered
Each real option and why it was not chosen.

## Consequences
Positive and negative results, follow-ups, and what this now constrains.

## Links
Related ADRs, docs, issues.
```

## Rules

- One decision per ADR. Number sequentially; never renumber.
- ADRs are **append-only**: to change a decision, add a new ADR and mark the old one *Superseded by ADR-XXXX* — don't rewrite history.
- Reference the ADR from the doc it affects (e.g. `TECH_STACK.md`), and vice versa.
- Keep it short: context + decision + consequences, not an essay.

## Accepted ADRs

| ID | Topic | Milestone | Status |
|---|---|---|---|
| ADR-0001 | Technology baseline | M1 | Accepted |
| ADR-0002 | Defer persistence to M3 | M1 | Accepted (superseded for security data by ADR-0003/0005) |
| ADR-0003 | User & role security model (+ id strategy for security tables) | M2 | Accepted |
| ADR-0004 | JWT authentication strategy | M2 | Accepted |
| ADR-0005 | Database migration & test-DB strategy | M2 | Accepted |
| ADR-0006 | Core domain ownership model | M3 | Accepted |
| ADR-0007 | Domain persistence & primary-key strategy | M3 | Accepted |
| ADR-0008 | Testcontainers PostgreSQL integration testing | M3 | Accepted |
| ADR-0009 | LLM provider abstraction & Ollama local-default strategy | M4 | Accepted |
| ADR-0010 | Structured LLM output strategy | M4 | Accepted |
| ADR-0011 | Tool abstraction & registry | M5 | Accepted |
| ADR-0012 | Tool authorization & execution-context boundary | M5 | Accepted |
| ADR-0013 | Agent decision contract | M6 | Accepted |
| ADR-0014 | Agent execution loop & cooperative budgets | M6 | Accepted |
| ADR-0015 | Agent / tool orchestration boundary | M6 | Accepted |
| ADR-0016 | Agent loop detection | M6 | Accepted |
| ADR-0017 | Redis conversation memory architecture | M7 | Accepted |
| ADR-0018 | Memory retention & bounding strategy | M7 | Accepted |
| ADR-0019 | Redis failure semantics | M7 | Accepted |
| ADR-0020 | Conversation ownership & isolation | M7 | Accepted |
| ADR-0021 | Guardrail policy engine | M8 | Accepted |
| ADR-0022 | Side-effect confirmation model | M8 | Accepted |
| ADR-0023 | Layered timeout strategy | M8 | Accepted |
| ADR-0024 | Confirmation integrity / action fingerprinting | M8 | Accepted |
| ADR-0025 | Per-user fixed-window rate limiting | M8 | Accepted |
| ADR-0026 | Durable agent audit model (3 typed tables) | M9 | Accepted |
| ADR-0027 | Audit transaction & failure semantics | M9 | Accepted |
| ADR-0028 | Audit payload privacy strategy | M9 | Accepted |
| ADR-0029 | Agent execution history API | M9 | Accepted |
| ADR-0030 | Observability metric taxonomy freeze + request correlation | M10 | Accepted |
| ADR-0031 | Audit retention enforcement (scheduled, batched, best-effort) | M10 | Accepted |

## Planned ADR topics (numbers assigned only when the decision is actually made)

| Topic | Expected milestone |
|---|---|
| Prometheus scrape-endpoint authentication (post-M10) | M12+ |
| JSON log layout for containerised deployments | M12 |
| Admin cross-user audit visibility | later (explicit decision) |

These are *planned topics*, not decisions. A new ADR file gets the next free number
when the decision is made; existing numbers are never renumbered or reused.
