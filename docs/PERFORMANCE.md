# Performance
## Agentic AI Task Orchestrator

> Conceptual. No performance work or measurements exist yet. **Never cite a number that wasn't actually measured on this system.**

## 1. Philosophy

Measure first, optimize the proven bottleneck, then re-measure. Correctness, security, and clarity come before speed. Most latency in an agentic system is the model and tool calls — optimize those paths, not micro-code.

## 2. What we will measure (targets are placeholders until benchmarked)

| Metric | How | Target (TBD — mark MEASURED only after benchmarking) |
|---|---|---|
| API latency p50/p95/p99 | Micrometer `http.server.requests` | — |
| DB query time | slow-query logging / timers | — |
| Redis latency | client metrics | — |
| Tool latency (per tool) | `agent.tool.duration` | — |
| LLM latency | `llm.request.duration` | — |
| Agent run duration | `agent.execution.duration` | — |
| Throughput / concurrent runs | load test | — |
| Failure rate under load | error counters | — |

## 3. Design guidance

- **Bound everything:** paginate queries, cap tool-call counts and payload sizes, set timeouts on every external call.
- **Transactions stay short and off the model path:** load → commit → call LLM/tool → persist in a new transaction.
- **Avoid N+1:** fetch joins / batch loading; index hot paths (`DATABASE.md`).
- **Cache deliberately:** Redis for stable, expensive, read-mostly results, with explicit TTL + invalidation (`MEMORY.md`). Applied after measurement, not by default.
- **Concurrency:** add async/pooling/virtual-threads only with a measured need (and an ADR if structural). Java 21 virtual threads are available if I/O-bound waiting proves to be the bottleneck.

## 4. Honesty rule

Performance claims (in docs, README, or a resume) are labeled MEASURED and cite the method, date, environment, and model used. Unmeasured numbers are never presented as fact. Placeholder targets are clearly marked TBD.

## 5. Testing

Benchmarks/load tests are separate from the correctness suite and are not gated in CI unless made deterministic. Record methodology so results are reproducible.
