# Prompt: Review agent behavior

Use when reviewing or changing the orchestrator, a tool, or a prompt.

---

**Change under review:** <tool / prompt / orchestrator logic>
**Evaluation dataset:** <path / cases affected>

## Checklist
1. **LLM-as-untrusted.** Is the model treated as a planner, not an authority? No direct DB/network/code access anywhere in the path?
2. **Tool contract.** Does each affected tool have typed input/output, validation, authorization, side-effect class, timeout, retry, and audit (per `docs/TOOL_SYSTEM.md`)?
3. **Argument validation.** Every model-generated argument validated against its schema before use?
4. **Authorization before effect.** Ownership/permission checked server-side before any side-effecting tool runs — not from a model/client claim?
5. **Least privilege.** Only context-appropriate tools are offered to the agent?
6. **Confirmation.** Dangerous/irreversible operations gated by explicit confirmation?
7. **Bounded execution.** Max tool calls, timeout, retry limit, loop/duplicate-call detection, cancellation all enforced (`docs/GUARDRAILS.md`)?
8. **Output validation.** Model output parsed into typed objects, validated, repaired/retried on failure; unsupported claims dropped?
9. **Injection resistance.** Untrusted text (user, tool outputs, external data) delimited so it can't override instructions?
10. **Observability & audit.** Every decision/selection/execution/side-effect/failure logged with execution + correlation IDs?
11. **Evaluation.** Prompt or tool-selection change re-run against the dataset? Tool-selection accuracy, argument accuracy, refusal behavior, and dangerous-op handling still pass?

## Output
Findings by severity with file:line and concrete fixes, plus which evaluation cases must be added or updated. Treat any prompt change as a behavior change.
