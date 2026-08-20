# Command: /refactor

Behavior-preserving cleanup, done safely.

**Usage:** `/refactor <target / smell>`

## Steps
1. **Load** the relevant `.claude/rules/*.md`. Invoke `engineering:tech-debt` to identify and prioritize.
2. **Establish a safety net.** Confirm tests exist for the code being refactored; add characterization tests first if coverage is thin. `./mvnw verify` must be green before you start.
3. **State the goal.** What smell is being removed and why (readability, coupling, duplication, performance). No scope creep into new features.
4. **Refactor in small steps**, keeping tests green after each. Preserve behavior and public contracts.
5. **Watch boundaries.** Don't collapse the Controller→Service→Repository layering or the agent/domain separation. Don't break DTO boundaries.
6. **Re-verify.** `./mvnw verify` green; behavior identical. If a contract legitimately changes, treat it as a feature and document it.
7. **Report** what changed structurally, why, and confirm no behavior change (with test evidence).

Never mix a refactor with a feature or a fix. Never refactor without a test safety net.
