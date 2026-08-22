# ADR-0027 — Audit Transaction & Failure Semantics

**Status:** Accepted · **Milestone:** M9 · **Date:** 2026-08-22

## Context
Audit must record execution facts durably, but it must never corrupt or block the agent/domain
execution path, and it must never hold a DB transaction across a slow LLM/tool call (a hard project
rule). We had to choose the transaction boundary and the failure policy.

## Decision
- **Best-effort, own transaction.** Each audit write runs in its own short transaction
  (`@Transactional(propagation = REQUIRES_NEW)`) that opens and commits around a single write only —
  never spanning an LLM or tool/domain transaction. A dedicated `AuditWriter` holds these methods so
  the `REQUIRES_NEW` boundary is a real proxy boundary; `AuditService` calls it and **swallows** any
  failure (WARN + `audit.write.failure` metric), never rethrowing into the agent path.
- **Repository-free seam.** The orchestrator/confirm service emit facts through an
  `AgentExecutionListener` (agent package, no-op default). The dependency runs one way (audit → agent);
  the agent core never depends on JPA/repositories, and audit is fully optional.
- **Honest limitation (documented):** a business action can succeed while its audit row is temporarily
  missing (recorded as `audit.write.failure`). Audit is authoritative **when present** because it is
  written from backend-observed facts, not reconstructed.

## Alternatives considered
- Fail-closed / hybrid fail-closed (rejected: couples audit availability to core functionality; wrong
  for an observational subsystem).
- Asynchronous/buffered writes or Kafka (rejected: unjustified complexity; synchronous per-event writes
  give incremental durability for short runs).

## Consequences
- Unbounded execution and torn transactions are impossible; audit never breaks a user's run.
- Verified by `AuditServiceTest` (swallow + metrics) and `AuditWriterTest` (idempotent writes).
