# Threat Model
## Agentic AI Task Orchestrator

> Threats specific to an agentic backend. Mitigations are planned alongside their milestones; this catalogue drives their design and tests.

## Method

For each threat: **impact · likelihood · mitigation · detection · testing**. Likelihood/impact are qualitative (Low/Med/High) for an internet-exposed single-tenant deployment. The guiding assumption: **the model can be manipulated; the backend must not be.**

---

### T1 — Direct prompt injection
- **Threat:** User input instructs the model to ignore rules, call forbidden tools, or exfiltrate data.
- **Impact:** High · **Likelihood:** High.
- **Mitigation:** Delimit user text as untrusted data; system instructions in a separate, non-overridable channel; least-privilege tool set; authorization enforced independent of model intent.
- **Detection:** Audit tool-selection vs. permitted set; alert on authorization denials and unusual tool sequences.
- **Testing:** Injection cases in the evaluation/security suite asserting refusal and no unauthorized tool call.

### T2 — Indirect prompt injection
- **Threat:** Malicious instructions embedded in data the agent reads (a task title, a tool's returned content, external data) hijack the loop.
- **Impact:** High · **Likelihood:** Med.
- **Mitigation:** Treat all tool outputs and external data re-fed to the model as untrusted and delimited; never let observations carry executable authority; authorization still gates every effect.
- **Detection:** Audit correlation between data content and subsequent tool calls; anomaly on effectful calls following untrusted reads.
- **Testing:** Cases with poisoned tool output asserting the agent does not escalate.

### T3 — Malicious tool arguments
- **Threat:** Model supplies crafted arguments (foreign resource IDs, oversized values, injection strings).
- **Impact:** High · **Likelihood:** Med.
- **Mitigation:** Validate every argument against a typed schema; parameterized queries only; re-check resource ownership at execution time.
- **Detection:** Log validation failures; alert on repeated invalid-argument attempts.
- **Testing:** Argument-validation and cross-user-ID tests per tool.

### T4 — Unauthorized tool invocation / privilege escalation
- **Threat:** Agent invokes a tool or targets a resource beyond the user's permissions.
- **Impact:** High · **Likelihood:** Med.
- **Mitigation:** Least-privilege tool exposure; server-side authorization before every effect; agent capped at the user's own permissions; admin tools never in a USER context.
- **Detection:** Audit every authorization decision; alert on denials and privilege-boundary hits.
- **Testing:** 403-path tests; a USER objective targeting another user's data must fail.

### T5 — Excessive tool usage / cost & DoS
- **Threat:** A prompt drives an unbounded or expensive loop (compute/cost exhaustion).
- **Impact:** Med · **Likelihood:** Med.
- **Mitigation:** Max tool calls, timeout, retry limit, loop detection, per-user rate limiting (`GUARDRAILS.md`).
- **Detection:** Metrics on tool-call counts and run duration; alert on budget exhaustion spikes.
- **Testing:** Guardrail-trip tests for each bound.

### T6 — Data exfiltration / sensitive info leakage
- **Threat:** Agent is coaxed into returning another user's data, secrets, or internal detail; or sensitive data leaves to an external provider.
- **Impact:** High · **Likelihood:** Med.
- **Mitigation:** Ownership-scoped reads; output validation; no secrets in prompts/outputs; external provider disabled by default and privacy-reviewed (`DATA_PRIVACY.md`).
- **Detection:** Audit read scope; egress review when fallback enabled.
- **Testing:** Cases asserting cross-user reads fail and outputs contain no secrets.

### T7 — Destructive / irreversible actions
- **Threat:** Agent deletes or externally sends without genuine user intent.
- **Impact:** High · **Likelihood:** Low–Med.
- **Mitigation:** High-risk classification + mandatory confirmation; re-authorize at execution; audit.
- **Detection:** Audit all high-risk invocations and their confirmations.
- **Testing:** High-risk tools must require confirmation and refuse without it.

### T8 — Malicious external API input (future tools)
- **Threat:** A future outbound tool (email, weather, knowledge) returns hostile content or is abused as an SSRF/exfil vector.
- **Impact:** Med · **Likelihood:** Low (until such tools exist).
- **Mitigation:** Validate/allowlist external endpoints; treat responses as untrusted; timeouts; no secrets in requests.
- **Detection:** Egress logging; anomaly on external call patterns.
- **Testing:** Add when the tool is built; mock hostile responses.

### T9 — Compromised credentials / stolen JWT
- **Threat:** An attacker with a valid token acts as the user, including via the agent.
- **Impact:** High · **Likelihood:** Low–Med.
- **Mitigation:** Short JWT TTL; BCrypt; rate limiting; (future) rotation/revocation; audit trail attributes actions to the principal.
- **Detection:** Audit unusual action patterns per principal.
- **Testing:** Auth expiry/invalid-token tests; rate-limit tests.

### T10 — Denial of service (request volume)
- **Threat:** Flooding auth or agent endpoints.
- **Impact:** Med · **Likelihood:** Med.
- **Mitigation:** Rate limiting; bounded per-run cost; timeouts.
- **Detection:** Metrics on request rate and error rate.
- **Testing:** Rate-limit tests.

---

## Residual risk & review

Model behavior is probabilistic; mitigations assume it can be wrong or adversarially steered — which is why **no security guarantee depends on the model**. This model is revisited whenever a new tool, external integration, or data category is added.
