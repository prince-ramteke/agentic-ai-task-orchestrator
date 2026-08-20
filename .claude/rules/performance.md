# Rule: Performance

Always-on constraints for efficiency and honest measurement. See `docs/PERFORMANCE.md`.

## Always
- Measure before optimizing. Profile or benchmark to find the real bottleneck.
- Paginate and bound every list query and external fetch. Cap agent tool-call counts and payload sizes.
- Keep DB transactions short; never span an LLM/tool call. Load → commit → call the model.
- Cache expensive, stable results in Redis with an explicit TTL when it demonstrably helps (see `docs/MEMORY.md`).
- Use appropriate indexes; avoid N+1 queries (fetch joins / batch loading).
- Set timeouts on every external call (LLM, tools, HTTP) and fail fast within guardrail bounds.
- Track latency percentiles (p50/p95/p99) via Micrometer for hot paths.

## Never
- Never claim a performance number that was not actually measured on this system.
- Never add a cache without an invalidation/TTL strategy.
- Never introduce speculative concurrency, pooling, or async complexity without a measured need and an ADR when structural.
- Never let LLM latency block a transaction or a request thread unnecessarily.

## Work that belongs here
Query/latency optimization, caching strategy, timeouts, concurrency, load/throughput measurement, and percentile tracking.

## Skills for this area
- **Auto-consult:** `engineering:tech-debt` when planning a performance refactor; `engineering:system-design` for structural changes.
- **Verify before done:** `engineering:code-review`, `superpowers:verification-before-completion` (with real measurements).
- **Ignore:** frontend/design and doc-format skills.
