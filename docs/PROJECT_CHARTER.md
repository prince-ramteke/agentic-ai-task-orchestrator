# Project Charter
## Agentic AI Task Orchestrator

## Why this project exists

To prove — with running, tested, observable code — that the author can build a **production-grade AI-enabled backend**, not merely call an LLM API. The central question a reviewer should be able to answer "yes" to after seeing it:

> **"Can this candidate build an AI agent system with real backend engineering — security, persistence, guardrails, observability, and evaluation — rather than a thin wrapper over a chat model?"**

## Resume / portfolio goals

The project is deliberately structured so each part maps to a claim a resume can make and an interviewer can probe:

| Capability area | What the project demonstrates |
|---|---|
| **Java backend** | Spring Boot, layered architecture, JPA, transactions, validation, exception handling |
| **API design** | REST contracts, DTO boundaries, status codes, versioning, Swagger/OpenAPI |
| **Security** | JWT auth, RBAC, per-resource ownership, authorization of AI-initiated actions, secrets hygiene |
| **GenAI / agents** | Spring AI, tool/function calling, multi-step orchestration, memory, guardrails, evaluation |
| **Systems thinking** | Postgres vs Redis roles, bounded execution, failure handling, idempotency, caching |
| **Reliability & safety** | Confirmation for dangerous ops, loop detection, retries, graceful degradation |
| **Observability** | Correlation/execution IDs, Micrometer metrics, Prometheus/Grafana, audit trail |
| **Quality** | Unit + integration (Testcontainers) + agent evaluation, coverage gate, CI |
| **Engineering maturity** | ADRs, threat model, data-privacy review, honest docs, measured (not invented) metrics |

## Target roles

Java Backend Engineer · Backend Engineer (AI/GenAI-focused) · AI/LLM Application Engineer · Platform/Systems Engineer working on agentic products.

## Learning objectives

- Implement LLM tool/function calling through a controlled registry, not ad-hoc prompt parsing.
- Design authorization for actions **initiated by an untrusted planner**.
- Build guardrails (bounds, confirmation, loop detection) that make an agent safe to run.
- Evaluate agent behavior systematically instead of eyeballing chat transcripts.
- Operate an AI backend: metrics, audit, and reproducible deployment.

## What "good" looks like

- The LLM is never the source of truth; the backend always is.
- Every claim in the docs and on the resume is backed by implemented, tested, verified code — labeled honestly (PLANNED/IMPLEMENTED/TESTED/VERIFIED/MEASURED).
- A reviewer can clone the repo, run one command, issue an objective, and inspect the audit trail of what the agent did and why it was allowed.

## Non-goals

Chasing framework breadth for its own sake; unmeasured performance claims; features that can't be demonstrated end-to-end; anything that requires the model to be trusted for correctness or security.

## Scope boundary for Milestone 0

This charter, the PRD, the roadmap, and the governance in `docs/` and `.claude/` — **no application code**. Implementation begins at Milestone 1.
