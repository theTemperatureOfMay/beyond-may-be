---
name: diagnosing-bugs
description: Diagnose hard bugs whose cause or reproduction is unclear, flaky failures, and performance regressions; write the evidence and recommended fix under .dev/logs without implementing it.
---

# Diagnosing Bugs

A discipline for hard bugs. Use this skill only when the root cause is unclear, reproduction is
difficult, the failure is flaky, or performance regressed. Simple obvious local bugs follow the normal
work classification and tdd path.

When exploring the codebase, read `CONTEXT.md` (if it exists) to get a clear mental model of the relevant modules, and check ADRs in the area you're touching.

## Diagnostic boundary

The boundary is a diagnosis report under `.dev/logs/`. Reproduce the symptom, test falsifiable
hypotheses, identify the cause or remaining uncertainty, and recommend a fix and regression seam.
Product fixes, permanent regression tests, commits, and PRs start only from a separate user request
and the repository's normal approval path.

Prefer read-only probes and temporary harnesses under `.dev/`. If diagnosis requires tracked-file
instrumentation, Git state changes, production instrumentation, or external state changes, show the
exact action and obtain the approval required by `AGENTS.md` before it runs. Restore diagnostic
changes before writing the report; preserve every pre-existing user change.

## Phase 1 — Build a feedback loop

**This is the skill.** Everything else is mechanical. If you have a **tight** pass/fail signal for the bug — one that goes red on _this_ bug — you will find the cause; bisection, hypothesis-testing, and instrumentation all just consume it. If you don't have one, no amount of staring at code will save you.

Prefer gradlew.bat, JUnit 5, Mockito, MockMvc, Testcontainers, and PowerShell for repository feedback
loops. Use the narrowest existing public seam that reproduces the user's symptom.

### Ways to construct one — try them in roughly this order

1. Run the narrowest relevant Gradle test, for example
   `.\gradlew.bat test --tests "<fully.qualified.TestName>"`.
2. Add or adapt a temporary JUnit test at an existing `Service` public method; use Mockito only for an
   established injected dependency such as a repository or external client.
3. Use MockMvc when the symptom is observable through HTTP status, body, validation, or security.
4. Use Testcontainers when PostgreSQL mappings, constraints, SQL, or transactions are load-bearing.
5. Use a PowerShell or HTTP reproduction script when a running application is required.
6. Replay a sanitized request, event, or log fixture through the smallest existing path that preserves
   the failure.
7. For performance regressions, establish a repeatable baseline with a profiler, timing harness, or
   query plan before comparing states.
8. Prepare bisection only when two states are known. Run `git bisect` only after the user explicitly
   authorizes its Git state changes.

Build the right feedback loop, and the diagnosis becomes tractable.

### Tighten the loop

Treat the loop as a product. Once you have _a_ loop, **tighten** it:

- Can I make it faster? (Cache setup, skip unrelated init, narrow the test scope.)
- Can I make the signal sharper? (Assert on the specific symptom, not "didn't crash".)
- Can I make it more deterministic? (Pin time, seed RNG, isolate filesystem, freeze network.)

A 30-second flaky loop is barely better than no loop; a 2-second deterministic one is tight — a debugging superpower.

### Non-deterministic bugs

The goal is not a clean repro but a **higher reproduction rate**. Loop the trigger 100×, parallelise, add stress, narrow timing windows, inject sleeps. A 50%-flake bug is debuggable; 1% is not — keep raising the rate until it's debuggable.

### When you genuinely cannot build a loop

Stop and say so explicitly. List what you tried. Ask the user for: (a) access to whatever environment reproduces it, (b) a captured artifact (HAR file, log dump, core dump, screen recording with timestamps), or (c) permission to add temporary production instrumentation. Without a loop, record an inconclusive report in Phase 5 instead of hypothesising.

### Completion criterion — a tight loop that goes red

Phase 1 is done when the loop is **tight** and **red-capable**: you can name **one command** — a script path, a test invocation, a curl — that you have **already run at least once** (paste the invocation and its output), and that is:

- [ ] **Red-capable** — it drives the actual bug code path and asserts the **user's exact symptom**. Not "runs without erroring" — it must be able to _catch this specific bug_.
- [ ] **Deterministic** — same verdict every run (flaky bugs: a pinned, high reproduction rate, per above).
- [ ] **Fast** — seconds, not minutes.
- [ ] **Agent-runnable** — it can run unattended. If manual interaction is unavoidable, request a
  captured artifact or the specific access needed instead of adding a repository interaction script.

If you catch yourself reading code to build a theory before this command exists, **stop — jumping straight to a hypothesis is the exact failure this skill prevents.** No red-capable command, no Phase 2.

## Phase 2 — Reproduce + minimise

Run the loop. Watch it go red — the bug appears.

Confirm:

- [ ] The loop produces the failure mode the **user** described — not a different failure that happens to be nearby. Wrong bug = wrong fix.
- [ ] The failure is reproducible across multiple runs (or, for non-deterministic bugs, reproducible at a high enough rate to debug against).
- [ ] You have captured the exact symptom (error message, wrong output, slow timing) as diagnosis evidence.

### Minimise

Once it's red, shrink the repro to the **smallest scenario that still goes red**. Cut inputs, callers, config, data, and steps **one at a time**, re-running the loop after each cut — keep only what's load-bearing for the failure.

Why bother: a minimal repro shrinks the hypothesis space in Phase 3 and gives the report a precise future regression seam.

Done when **every remaining element is load-bearing** — removing any one of them makes the loop go green.

Do not proceed until you have reproduced **and** minimised.

## Phase 3 — Hypothesise

Generate **3–5 ranked hypotheses** before testing any of them. Single-hypothesis generation anchors on the first plausible idea.

Each hypothesis must be **falsifiable**: state the prediction it makes.

> Format: "If <X> is the cause, then <changing Y> will make the bug disappear / <changing Z> will make it worse."

If you cannot state the prediction, the hypothesis is a vibe — discard or sharpen it.

**Show the ranked list to the user before testing.** They often have domain knowledge that re-ranks instantly ("we just deployed a change to #3"), or know hypotheses they've already ruled out. Cheap checkpoint, big time saver. Don't block on it — proceed with your ranking if the user is AFK.

## Phase 4 — Instrument

Each probe must map to a specific prediction from Phase 3. **Change one variable at a time.**

Tool preference:

1. **Debugger / REPL inspection** if the env supports it. One breakpoint beats ten logs.
2. **Targeted logs** at the boundaries that distinguish hypotheses.
3. Never "log everything and grep".

**Tag every debug log** with a unique prefix, e.g. `[DEBUG-a4f2]`. Cleanup at the end becomes a single grep. Untagged logs survive; tagged logs die.

Use the diagnostic boundary for every probe. Keep repository instrumentation temporary and approved,
remove every tagged probe before the report, and record the observation rather than the instrumentation.

**Perf branch.** For performance regressions, establish a baseline measurement with a repeatable timing
harness, Java profiler, database statistics, or query plan, then compare one variable at a time. Measure
first; recommend the fix in the report.

## Phase 5 — Write the diagnosis report

Write `.dev/logs/YYMMDD-bug-diagnosis-<slug>.md`. `.dev/` is a personal work record, not a
canonical project document. Use this structure:

```markdown
# Bug diagnosis: <title>

- Date:
- Status: confirmed / inconclusive / blocked

## Symptom and impact
## Reproduction
- Red-capable command:
- Sanitised result:

## Evidence and cause
- Confirmed cause or highest-confidence explanation:
- Hypotheses tested and falsified:

## Recommended fix — not applied
## Recommended regression seam — not added
## Unverified and blocked items
## Commands and results
```

Record sanitised evidence rather than secrets, credentials, personal data, or raw protected-file
contents. If there is no correct regression seam, make that an architectural finding in the report.
Recommend `plan`, `tdd`, `implement`, or architectural follow-up only as the next separately requested
task.

### Completion criterion

The skill is complete when the report exists, every temporary diagnostic change made by this run is
removed, pre-existing user changes remain intact, and confirmed facts are separated from uncertainty.
The skill ends at the diagnosis report.
