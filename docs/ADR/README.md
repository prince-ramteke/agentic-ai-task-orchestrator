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

## Planned ADRs (to be written as decisions are made — none accepted yet)

| ID | Topic | Expected milestone |
|---|---|---|
| ADR-0001 | Technology stack | M1 |
| ADR-0002 | Agent orchestration model | M6 |
| ADR-0003 | PostgreSQL as primary store (+ id strategy) | M3 |
| ADR-0004 | Redis for conversation/execution memory | M7 |
| ADR-0005 | Tool authorization model | M5 |
| ADR-0006 | Confirmation policy for dangerous operations | M8 |
| ADR-0007 | LLM provider strategy (Ollama default, fallback) | M4 |

These are *planned topics*, not decisions. An ADR file is created only when the decision is actually made.
