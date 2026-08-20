# Prompt: Review performance

Use to review a change for efficiency. Measure, don't guess.

---

**Under review:** <path / component>
**Measurements available:** <benchmarks / profiler output / none yet>

## Checklist
1. **Evidence first.** Is there a measurement identifying the real bottleneck? If not, say so — do not optimize speculatively.
2. **Queries.** Indexed hot paths? No N+1? Pagination and bounded result sizes everywhere?
3. **Transactions.** Short; none spanning an LLM/tool call (load → commit → call).
4. **External calls.** Timeouts on every LLM/tool/HTTP call; failures fast within guardrail bounds.
5. **Caching.** Any cache has an explicit TTL and invalidation strategy (`docs/MEMORY.md`); Redis used for ephemeral/cache only.
6. **Concurrency.** No speculative async/pooling without a measured need (and an ADR if structural).
7. **Latency tracking.** p50/p95/p99 captured for the hot path via Micrometer.

## Output
Findings by severity with concrete, measured recommendations. Never cite a performance number that wasn't actually measured on this system.
