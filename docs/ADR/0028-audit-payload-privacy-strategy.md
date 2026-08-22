# ADR-0028 — Audit Payload Privacy Strategy

**Status:** Accepted · **Milestone:** M9 · **Date:** 2026-08-22

## Context
An audit store that captured raw prompts, tool arguments, tool results, or model reasoning would become
a data-leak liability. We had to decide exactly what is persisted.

## Decision
Persist **safe metadata + hashes + bounded summaries only**:
- **Persisted:** tool name, risk level, outcome/status, error codes, durations/timestamps, counts,
  correlation ids (`execution_uid`/`conversation_id`/`request_id`/`tool_execution_uid`), an
  `arguments_hash` (SHA-256 of the canonical args, reusing the M8 `FingerprintService`
  canonicalization), a length-capped `final_response_summary`, and a length-capped `result_summary`.
- **Never persisted:** raw prompts, raw tool arguments, raw tool results, system prompts, hidden
  reasoning / **chain-of-thought**, JWTs, passwords, secrets, or confirmation secrets.
- **Bounded & configurable:** summary caps via `audit.final-summary-max-chars` /
  `audit.result-summary-max-chars` (default 500, ≤ the 600-char DB columns), applied by the recorder.
- **Sanitized API:** responses are DTOs — no internal class names, stack traces, raw payloads, or
  secrets; `conversation_id` is metadata, never an authorization claim.

## Consequences
- Audit is useful for reconstructing *what happened* without storing sensitive content.
- Chain-of-thought is a strict never — enforced by design (no field for it) and by security tests.
- Verified by `AuditWriterTest` (bounding), `AgentAuditControllerTest` and `AgentAuditE2EIT`
  (no raw content exposed).
