# Prompt: Debug a production issue

Use for a defect, failure, or unexpected behavior. Reproduce before fixing.

---

**Symptom:** <what's wrong, where, since when>
**Correlation/execution ID:** <if available>
**Environment:** <dev | docker | prod>

## Do this in order
1. **Invoke** `superpowers:systematic-debugging` (and `engineering:debug`). Do not propose a fix before the cause is known.
2. **Gather evidence.** Reproduce with a concrete case. Pull logs by correlation/agent-execution/tool-execution ID. Note inputs, outputs, timings, and which guardrail/limit (if any) tripped.
3. **Form one hypothesis at a time.** Test it. For agent issues, trace decision → argument → authorization → tool execution → observation and find where reality diverged from expectation.
4. **Confirm the root cause** explicitly — not a symptom.
5. **Write a failing regression test** that reproduces it.
6. **Fix minimally.** Only what the root cause requires.
7. **Verify.** `./mvnw verify` green; the regression test passes; nothing else broke.
8. **Look for siblings.** Same bug class elsewhere?
9. **Report** root cause, fix, regression test, verification evidence, and any doc/changelog update.

## Constraints
No speculative fixes. No swallowing the error. Preserve behavior except the bug. If prompts changed as part of the fix, re-run the evaluation suite.
