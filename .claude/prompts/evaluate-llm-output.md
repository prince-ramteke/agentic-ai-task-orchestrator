# Prompt: Evaluate LLM / agent output

Use to assess agent behavior against the evaluation dataset — not just "did it return 200".

---

**Change / model / prompt under evaluation:** <what changed>
**Dataset:** <path to cases in `docs/EVALUATION.md` format>

## Do this in order
1. **Load** `docs/EVALUATION.md` and the dataset. Each case has: input, expected tool(s), expected arguments, expected authorization result, expected final outcome.
2. **Run** the agent over the dataset with deterministic settings and the LLM provider mocked/pinned where required (no unbounded live calls in CI).
3. **Score each dimension:**
   - Tool-selection accuracy (right tools, no unnecessary calls).
   - Argument accuracy (valid, correct values).
   - Authorization behavior (refuses unauthorized actions).
   - Dangerous-operation handling (requests confirmation; doesn't act without it).
   - Task completion / final-answer correctness.
   - Failure recovery (recovers or fails gracefully within bounds).
   - Latency (measured, not assumed).
4. **Compare** against the previous baseline. A prompt or tool-selection change must not regress any dimension without an explicit, justified decision.
5. **Report** per-dimension scores, regressions, and any new cases that should be added to the dataset.

## Constraints
Never report a score that wasn't actually produced by a run. Treat a prompt change as a behavior change requiring re-evaluation. Keep the dataset reproducible.
