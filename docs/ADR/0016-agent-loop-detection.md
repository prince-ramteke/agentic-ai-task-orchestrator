# ADR-0016: Agent Loop Detection

- **Status:** Accepted
- **Date:** 2026-08-21
- **Deciders:** Prince + Claude

## Context

A bounded run can still waste its whole budget by repeating the same tool call — the model asks for the
identical action over and over, making no progress. The iteration and tool-call budgets eventually stop
this, but only after the budget is spent. A cheaper, earlier stop for obvious non-progress is worth
having in M6, provided it is deterministic and simple.

## Decision

`LoopDetector` fingerprints each intended tool call as `toolName + canonicalArguments`, where
`canonicalArguments` is a stable, key-sorted JSON rendering of the argument map (so argument key order
cannot defeat detection). It counts occurrences; when a fingerprint would occur more than
`loopThreshold` (env `AGENT_LOOP_THRESHOLD`, default 2) times, the run stops with `LOOP_DETECTED` /
`AGENT_LOOP_DETECTED`. This catches "same tool + same arguments repeated" and "same decision repeated N
times". It is deliberately simple — no semantic similarity, no ML.

## Alternatives considered

- **No loop detection, rely only on budgets** — rejected: wastes the whole budget on obvious
  non-progress before stopping.
- **Semantic/embedding-based non-progress detection** — rejected as over-engineered for M6; richer
  non-progress detection can be added in M8 alongside the enforcement layer.
- **Detect only immediate consecutive duplicates** — rejected: a count-with-threshold over the run also
  catches alternating repeats and is barely more code.

## Consequences

- Cheap, deterministic early stop for the most common runaway pattern; fully unit-tested.
- The threshold is env-tunable; the fingerprint is order-insensitive.
- A legitimate repeated call (same tool/args used more than the threshold on purpose) would trip it;
  acceptable in M6, and revisitable in M8 if a real workflow needs it.

## Links

- Spec §11, §16. ADR-0014.
