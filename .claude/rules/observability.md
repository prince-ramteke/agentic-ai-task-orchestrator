# Rule: Observability

Always-on constraints for logging, metrics, and traceability. See `docs/OBSERVABILITY.md` and `docs/AUDIT_LOGGING.md`.

## Always
- Use SLF4J structured logging. Attach a **correlation ID** to every request and propagate it.
- Attach an **agent execution ID** to every agent run and a **tool execution ID** to every tool call; include both in related logs, metrics, and audit records.
- Emit metrics via Micrometer for: request latency, error/failure rate, tool-execution count, agent-execution success rate, LLM request duration, and (when available) token usage.
- Log at the right level: DEBUG for developer detail, INFO for lifecycle events, WARN for recoverable issues, ERROR for failures. Include enough context to act.
- Make every failure observable — a swallowed error is a bug.
- Expose `/actuator/health` and metrics endpoints for Prometheus scraping.

## Never
- Never log secrets, tokens, passwords, full prompts, or full tool payloads (redact per `docs/DATA_PRIVACY.md`).
- Never emit an unbounded-cardinality metric label (e.g. raw user text or full IDs as label values).
- Never rely on `System.out`/`printStackTrace` for diagnostics.
- Never let an agent step run without a log/audit trail.

## Work that belongs here
Structured logging, correlation/execution IDs, Micrometer metrics, dashboards, health checks, and log hygiene.

## Skills for this area
- **Auto-consult:** `engineering:system-design` when designing the metric/trace model.
- **Verify before done:** `engineering:code-review`. Read `rules/security` and `docs/DATA_PRIVACY.md` for redaction.
- **Ignore:** frontend/design and doc-format skills. `engineering:incident-response` is manual, for live incidents only.
