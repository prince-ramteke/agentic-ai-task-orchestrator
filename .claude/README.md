# `.claude/` — Claude Code operating system for this repo

This directory is the machine-readable half of the project's engineering governance. The human-readable half lives in [`../docs/`](../docs). Together with [`../CLAUDE.md`](../CLAUDE.md), they make AI-assisted work on this repo consistent and disciplined.

## What's here

| Folder | Purpose | When it applies |
|---|---|---|
| `rules/` | **Always-on constraints.** Short, enforceable rules per area (backend, ai-agent, security, database, api, testing, observability, performance, architecture, documentation, git). | Read the matching rule file every time you touch that area. Rules are not optional. |
| `commands/` | **Reusable workflows** invoked as `/command`. Each is an ordered, disciplined procedure (understand → read docs → plan → implement → test → document → verify). | When starting a task that matches a command (e.g. `/new-feature`, `/fix-bug`, `/security-review`). |
| `prompts/` | **Fill-in-the-blank templates** for recurring engineering tasks (build a feature, debug production, review agent behavior, prepare a release). | When you want a structured prompt to drive a specific piece of work; commands often call these. |

## How the pieces fit together

```
CLAUDE.md ............ master rules + required workflow (read first)
  └─ docs/ ........... source of truth (specs, architecture, contracts)
  └─ .claude/rules/ .. always-on constraints, enforced on every change
  └─ .claude/commands/ ordered workflows (/new-feature calls prompts/build-feature)
  └─ .claude/prompts/ . reusable templates the commands and you invoke
  └─ docs/SKILL_ROUTING_MAP.md ... which external skill for which task
```

**Precedence:** `CLAUDE.md` and `docs/` outrank `.claude/rules/`, which outrank any external skill. When a skill suggests something that conflicts with a repo rule, follow the repo and note the conflict.

## How to use commands

Type `/<command>` (e.g. `/new-feature agent execution history`). The command tells Claude which docs and rules to load, which skills to consult, and the exact order of steps. Commands enforce plan-first, tests-and-review-before-done. They never say merely "implement this."

## How to use prompts

Open the prompt, fill in the bracketed placeholders, and follow the steps top to bottom. Prompts are the reusable building blocks; commands orchestrate them.

## How rules work

Each `rules/*.md` file has an **Always** list and a **Never** list plus the scope of work it governs and which skills to consult. They are concise on purpose — a rule no one reads is worse than no rule. If reality changes, update the rule in the same change that changes the behavior.

## How skill routing works

`docs/SKILL_ROUTING_MAP.md` maps task areas to the **actually available** skills (`superpowers:*`, `engineering:*`, `frontend-design`, `ui-styling`, …). Claude consults it to pick the fewest skills that fit. It never invents skill names; where no skill fits, it uses the standard Claude Code engineering workflow.

## Adding new commands / prompts / rules

- **New rule:** only for a genuinely new always-on constraint. Keep it to Always/Never plus scope. Don't duplicate an existing rule — cross-reference instead.
- **New command:** must be a real, ordered workflow that enforces the Definition of Done. Update this README's list and `CLAUDE.md` §11.
- **New prompt:** must be reusable and parameterized, not a one-off.
- **New skill in the environment:** add one row to `docs/SKILL_ROUTING_MAP.md` and decide its priority/area before using it.

Keep everything short, actionable, and non-duplicative.
