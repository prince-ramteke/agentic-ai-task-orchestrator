# Rule: Git & Workflow

Always-on constraints for version control and change hygiene.

## Always
- Make small, single-purpose changes. One feature/bugfix per branch and change set.
- Branch off `main`; use a descriptive branch name (`feat/…`, `fix/…`, `docs/…`, `chore/…`).
- Write meaningful commit messages: imperative subject line (≤ ~72 chars), body explaining *why* when non-obvious.
- Review your own `git diff` before committing. Keep unrelated changes out.
- Keep CI green. A change is not done until the build and tests pass (see `docs/DEFINITION_OF_DONE.md`).
- Update docs and `docs/CHANGELOG.md` in the same change that alters behavior.
- Record significant decisions as ADRs (`docs/ADR/`).

## Never
- Never commit secrets, `.env`, credentials, tokens, or generated artifacts (see `.gitignore`).
- Never commit or push unless the user asked for it.
- Never force-push shared branches or rewrite published history without explicit approval.
- Never merge with red CI or failing tests.
- Never bundle a refactor and a feature and a fix into one commit.

## Commit message convention
```
<type>: <imperative summary>

<why the change is needed / what it enables>
```
`type` ∈ feat | fix | docs | refactor | test | chore | perf | build | ci.

## Work that belongs here
Branching, commits, PR hygiene, change scoping, and keeping history clean and reviewable.

## Skills for this area
- **Auto-consult:** `superpowers:requesting-code-review` before merge; `superpowers:finishing-a-development-branch` when integrating completed work.
- **Verify before done:** `superpowers:verification-before-completion`.
- **Ignore:** design and doc-format skills.
