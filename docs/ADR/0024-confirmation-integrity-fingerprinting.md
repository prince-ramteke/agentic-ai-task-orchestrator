# ADR-0024 — Confirmation Integrity / Action Fingerprinting

**Status:** Accepted · **Milestone:** M8 · **Date:** 2026-08-22

## Context
A confirmation must approve one *exact* action and nothing else. It must resist replay, argument
mutation, cross-user use, cross-conversation use, and tampering of the stored record.

## Decision
- **Fingerprint = SHA-256** hex over exactly five bound fields: `userId`, `conversationId`, `toolName`,
  canonical arguments, and `riskLevel`, joined by an unforgeable control-character separator.
  Arguments are canonicalized to sorted-key JSON so equal maps hash equally regardless of order. Any
  change to any bound field changes the fingerprint.
- **Storage:** one application-owned JSON blob in Redis under `guard:confirmation:{id}` (a namespace
  separate from `conv:{userId}:{conversationId}`), TTL `AGENT_CONFIRMATION_TTL_SECONDS` (default 300).
- **Single-use, atomic:** confirm peeks and checks ownership + fingerprint + clock-based expiry
  *without deleting*, then consumes via Redis `GETDEL` (`getAndDelete`). A replay or a concurrent second
  confirm finds nothing → rejected; the action runs **at most once**. Execution happens in the caller
  only after a non-null consume.
- **Ownership before delete:** a foreign id is masked as `CONFIRMATION_NOT_FOUND` and never consumes
  another user's pending action.
- **No client arguments** at confirm time — the stored action is what executes.

## Consequences
- Replay, mutation, cross-user, cross-conversation, expiry, and tamper are all rejected with stable
  codes (`CONFIRMATION_NOT_FOUND/EXPIRED/MISMATCH/ALREADY_USED`).
- No distributed transaction/lock is introduced; `GETDEL` provides the atomicity needed.
- Verified by `FingerprintServiceTest`, `RedisConfirmationServiceTest`, `AgentGuardrailConfirmationIT`.
