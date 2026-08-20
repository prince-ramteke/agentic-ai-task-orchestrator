# Rule: AI / Agent

Always-on constraints for the agent, tools, and LLM integration. This is the highest-risk area of the system. See `docs/AGENT_ARCHITECTURE.md`, `docs/TOOL_SYSTEM.md`, `docs/GUARDRAILS.md`, `docs/THREAT_MODEL.md`.

## Always
- Treat the LLM as an **untrusted planner**. The application, not the model, is the source of truth.
- Access the model only through the provider abstraction (`LlmClient`), never a vendor SDK from a feature.
- Register every tool explicitly with: unique name, description, typed input, typed output, authorization requirement, side-effect classification, validation rules, timeout, retry policy, audit hook (see `docs/TOOL_SYSTEM.md`).
- **Validate every model-generated tool argument** against its schema before use.
- **Authorize before execution:** a tool verifies the authenticated user's permission on the target resource before doing anything.
- Offer the agent only the tools allowed in the current context (least privilege).
- Require explicit confirmation for dangerous/side-effecting-and-irreversible operations (delete, external send, critical modification).
- Bound every agent run: max tool calls, timeout, retry limit, loop/duplicate-call detection, cancellation (see `docs/GUARDRAILS.md`).
- Validate all LLM output against a typed schema; parse into objects; repair/retry on malformed output; drop unsupported claims.
- Delimit all untrusted text (user input, tool outputs re-fed to the model, external data) so it cannot override system instructions.
- Log every significant step: decision, tool selected, arguments (redacted), authorization result, execution result, failure — with execution ID + correlation ID.
- Evaluate agent behavior with a representative dataset before shipping a change (see `docs/EVALUATION.md`).

## Never
- Never allow the model direct SQL, filesystem, network, or code-execution access.
- Never execute arbitrary model-generated code or shell.
- Never trust a tool argument, target ID, or authorization claim produced by the model.
- Never let a tool run before its authorization and input validation pass.
- Never return raw, unvalidated model text as an API result.
- Never send sensitive data to an external model provider unless fallback is explicitly enabled and privacy-reviewed (`docs/DATA_PRIVACY.md`).
- Never let an agent loop unbounded or retry without a limit.

## Prompt changes are behavior changes
A prompt edit is a code change: review it, version it, and re-run the evaluation suite. Prompts live in code/config, not scattered in strings.

## Work that belongs here
The orchestrator, tool registry, tool implementations, LLM provider abstraction, prompt templates, guardrails, and agent evaluation.

## Skills for this area
- **Auto-consult:** `engineering:system-design` (tool/agent contracts). Always read `rules/security` and `rules/testing`.
- **Verify before done:** `engineering:code-review` (injection/authorization gaps), `superpowers:verification-before-completion`, plus the evaluation suite.
- **Ignore:** frontend/design and doc-format skills.
