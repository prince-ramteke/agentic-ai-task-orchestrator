# Command: /security-review

Targeted security + agent-safety review of a change or component.

**Usage:** `/security-review <component / diff / feature>`

## Steps
1. **Load** `docs/SECURITY.md`, `docs/THREAT_MODEL.md`, `.claude/rules/security.md`, `.claude/rules/ai-agent.md`. Invoke `engineering:code-review`.
2. **AuthN/AuthZ.** Every endpoint authenticated by default? Ownership enforced server-side before any effect? Admin routes gated by role?
3. **Input & argument validation.** All request DTOs validated? All model-generated tool arguments validated before use? File uploads checked by type + magic bytes + size?
4. **Agent-specific threats.** Walk `docs/THREAT_MODEL.md`: prompt injection (direct + indirect), unauthorized tool invocation, privilege escalation, excessive tool usage, data exfiltration, destructive actions. For each relevant one, confirm the mitigation exists and is tested.
5. **Secrets & logging.** No secrets in code/git. No secrets, tokens, PII, or full prompts/payloads in logs (`docs/DATA_PRIVACY.md`).
6. **Dangerous operations.** Confirmation required and enforced? Bounded execution in place?
7. **Report** findings by severity with concrete fixes, and list the security tests that should exist (401/403, injection, authorization refusal).

Read-only review. Produces findings + required tests, not fixes (hand fixes to `/fix-bug` or `/new-feature`).
