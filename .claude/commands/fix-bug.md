# Command: /fix-bug

Disciplined, reproduce-first bug fix. No guess-and-check.

**Usage:** `/fix-bug <symptom / where it happens>`

## Steps
1. **Invoke** `superpowers:systematic-debugging` (and `engineering:debug`). Follow it — do not propose a fix before understanding the cause.
2. **Reproduce** the failure with a concrete case. Capture the exact error, logs (use the correlation/execution ID), and inputs.
3. **Isolate** the root cause. State it explicitly. If it's in the agent/tool path, trace the decision → argument → authorization → execution chain.
4. **Write a failing test** that captures the bug (regression test) before fixing.
5. **Fix minimally.** Change only what the root cause requires. No opportunistic refactors.
6. **Confirm** the new test passes and no others broke: `./mvnw verify`.
7. **Check for siblings.** Does the same class of bug exist elsewhere? Note or fix consistently.
8. **Document** if the fix changes behavior/contract (`docs/*.md`, `docs/CHANGELOG.md`).
9. **Report** the root cause, the fix, the regression test, and verification evidence.

Enforces: reproduce → failing test → minimal fix → verify. Never claim fixed without a passing regression test.
