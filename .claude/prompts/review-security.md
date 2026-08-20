# Prompt: Review security

Use to security-review a change, endpoint, or component.

---

**Under review:** <component / diff / feature>

## Checklist
1. **Authentication.** Authenticated by default? Only intended routes public? JWT validated, short-lived?
2. **Authorization.** RBAC on admin routes? Ownership enforced server-side before any effect, in both services and tools? No authorization decision trusted from client or model?
3. **Input validation.** All request DTOs validated? File uploads checked by content type + magic bytes + size? All model-generated tool arguments validated?
4. **Agent threats** (`docs/THREAT_MODEL.md`). Prompt injection (direct + indirect), unauthorized tool invocation, privilege escalation, excessive tool usage, data exfiltration, destructive actions — mitigation present and tested for each relevant one?
5. **Secrets.** None in code/git. Config from env vars. `.env.example` current.
6. **Logging/PII.** No secrets, tokens, passwords, PII, or full prompts/payloads in logs (`docs/DATA_PRIVACY.md`)?
7. **Dangerous operations.** Confirmation required and enforced? Bounded execution in place?
8. **External providers.** Sensitive data not sent to an external model unless fallback is explicitly enabled and privacy-reviewed?
9. **Error handling.** No stack traces or internal detail leaked to clients; errors routed through the global handler?

## Output
Findings ranked by severity with concrete fixes, and the list of security tests that must exist (401/403 paths, injection resistance, authorization refusal). No secret ever proposed to be committed.
