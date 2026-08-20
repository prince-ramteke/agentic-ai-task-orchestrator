# Command: /ship-milestone

Close out a roadmap milestone against its Definition of Done.

**Usage:** `/ship-milestone <M?>`

## Steps
1. **Load** `docs/ROADMAP.md` (the milestone's objective, outputs, validation, DoD), `docs/DEFINITION_OF_DONE.md`, and `docs/RELEASE_CHECKLIST.md`.
2. **Confirm scope.** Every output listed for the milestone is actually implemented — no more, no less. List any gaps.
3. **Validate.** Run the milestone's validation steps and `./mvnw verify`. Capture evidence. For agent milestones, run the evaluation suite (`docs/EVALUATION.md`).
4. **Review.** Run `/review-code` and, if auth/agent touched, `/security-review` over the milestone's changes.
5. **Docs.** All affected `docs/*.md`, Swagger, README status table, and `docs/CHANGELOG.md` updated and truthful. ADRs recorded for significant decisions.
6. **Verify DoD** item by item (`docs/DEFINITION_OF_DONE.md`). Nothing marked done without evidence.
7. **Report** the milestone summary: what shipped (IMPLEMENTED/TESTED/VERIFIED), what's still PLANNED, evidence, risks, and the recommended next milestone. Do not auto-start the next one.

Enforces evidence-based milestone completion. No milestone is "shipped" on assertion alone.
