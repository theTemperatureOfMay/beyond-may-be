---
name: wayfinder
description: Plan a foggy, unresolved multi-session effort as a local Markdown decision map, applying tracker-file changes only after explicit user approval.
---

A loose idea has arrived — too big for one agent session, and wrapped in fog: the way from here to the **destination** isn't visible yet. Wayfinding is about finding that way, not charging at the destination. This skill charts the way as a **shared local Markdown map**, then works its **decision files** — questions whose resolution is a decision, not slices of a build to execute — one at a time until the route is clear.

The destination varies per effort, and naming it is the first act of charting — it shapes every decision. It might be a spec to hand off and iterate on, a decision to lock before planning starts, or a change made in place like a data-structure migration. The map is domain-agnostic.

## Plan, don't do

Wayfinder is planning: each file resolves a decision, and the map is done when the way is clear —
nothing remains to decide before another stage does the work. Produce decisions, not deliverables,
and do not create a spec, implementation plan, implementation ticket, or code change. Decision-support
research and throwaway prototypes may inform a decision, but they do not deliver the destination or
bypass the handoff.

## Local write gate

Automatic selection permits reading and classifying local tracker state, exploring repository
context, and drafting file changes without approval. Do not change tracker files before this gate
passes.

Before each write batch:

1. Re-read every target because another session may have changed it.
2. Show the final local file batch exactly, including paths, full content or edits, `Status` values,
   and `Blocked by` edges.
3. Ask for explicit user approval for that exact batch immediately before applying it. Invoking
   wayfinder or approving a destination, decision, map, or earlier draft does not approve an
   unshown write.
4. Apply only the approved batch, verify the files and frontier, and report it. If any target or
   content changes, stop, show the revised batch, and obtain fresh approval.

Without approval, return the draft and stop without changing tracker files. Git, repository source
files, services, and external systems keep their own approval rules from `AGENTS.md`; local tracker
approval never grants them implicitly.

## Refer by name

Every map and decision has a **name** — its title. In narration and the map's Decisions-so-far,
refer to it by that name, never by a bare number or slug. The name wraps its relative file link.

## The Map

The map is the initiative's `map.md`. Its decisions are files under `decisions/`.

The map is an **index**, not a store. It lists the decisions made and points at the files that hold their detail; a decision lives in exactly one place, so the map never restates it, only gists it and links.

This project stores `map.md`, decision files, textual blocking edges, and the frontier through
`docs/agents/issue-tracker.md`. If that configuration is missing, stop; do not invent another path
or fall back to GitHub.

### The map body

The whole map at low resolution, loaded once per session. Open decisions stay in their own files
and are found by their `Status` and `Blocked by` fields.

```markdown
## Destination

<what reaching the end of this map looks like — the spec, decision, or change this effort is finding its way to.>

## Notes

<domain; skills every session should consult; standing preferences for this effort>

## Decisions so far

<!-- one line per resolved decision: enough to judge relevance, then follow the relative link -->

- [<resolved decision title>](decisions/NN-<slug>.md) — <one-line gist of the answer>

## Not yet specified

<!-- see "Fog of war": in-scope fog not yet precise enough for a decision file -->

## Out of scope

<!-- see "Out of scope": work ruled beyond the destination; closed, never graduates -->
```

### Decision files

Each decision is one `decisions/NN-<slug>.md` file, sized to one session:

```markdown
# <decision title>

Status: open

Type: <research|prototype|grilling|task>

Blocked by: <NN, NN or None>

## Question

<the decision or investigation this file resolves>

## Answer

<leave empty until resolution>

## Comments
```

Each decision has one `Type` — `research`, `prototype`, `grilling`, or `task`.

A session **claims** a decision by changing it to `Status: claimed` so concurrent sessions skip it.
Preview the exact claim and pass the [Local write gate](#local-write-gate) before ownership-dependent
work. Re-check the frontier when applying the claim.

Blocking uses the textual `Blocked by` field from `docs/agents/issue-tracker.md`. A decision is
unblocked only when every referenced decision has `Status: resolved`; the **frontier** is the open,
unblocked, unclaimed decisions, ordered by file number.

Record the answer under `## Answer` on resolution. Link supporting assets instead of pasting them.

## Decision Types

Every decision is either **HITL** — human in the loop, worked *with* a human who speaks for themselves — or **AFK**, driven by the agent alone. A HITL decision only resolves through that live exchange; the agent never stands in for the human's side of it.

- **Research** (AFK): Reading documentation, third-party APIs, or local resources like knowledge bases to surface a fact a decision waits on. Use `/research` when knowledge outside the current working directory is required.
- **Prototype** (HITL): Raise the fidelity of the discussion with the smallest cheap artifact needed — an outline, rough take, stub, or throwaway UI/logic code. Link the artifact. Use when "how should it look" or "how should it behave" is the key question.
- **Grilling** (HITL): Conversation via `/grill` in its one-question route, with `/domain-modeling` when the map needs domain records. The default case.
- **Task** (HITL or AFK): Manual work that must happen before a *decision* can be made — nothing to decide, prototype, or research, but the discussion is blocked until it's done. It earns its place by unblocking a decision, not by delivering the destination. External actions still follow `AGENTS.md`. Resolved when the work is done; the answer records only non-sensitive facts later decisions need.

## Fog of war

The map is _deliberately_ incomplete: don't chart what you can't yet see. Beyond the live decisions lies the **fog of war** — the dim view of decisions and investigations you can tell are coming but can't yet pin down, because they hang on questions still open. Resolving a decision clears the fog ahead of it, graduating whatever is now specifiable into fresh decision files until the way to the destination is clear.

The map's **Not yet specified** section is where that dim view is written down: the suspected question, the area to revisit later. It is in scope, just not sharp enough for a decision file.

**Fog or decision?** The test is whether you can state the question precisely now — _not_ whether you can answer it now.

- **Decision file when** the question is already sharp — even if it is blocked.
- **Not yet specified when** you cannot yet phrase it that sharply. Do not pre-slice the fog.

**Not yet specified** excludes what is already decided, already a live decision, or out of scope.

## Out of scope

Fog only ever gathers _toward_ the destination. The destination fixes the scope, so work beyond it is **out of scope** — it isn't fog, and it doesn't belong in **Not yet specified**. It gets its own **Out of scope** section on the map: work you've consciously ruled out of _this_ effort. Scope, not sharpness, lands it here.

Out-of-scope work never graduates — the frontier stops at the destination — so it returns only if the destination is redrawn, and then as a fresh effort, not a resumption.

Ruling something out of scope is a scoping act, not a step on the route. When an existing decision
turns out to sit past the destination, record that answer, set it to `Status: resolved`, link it from
**Out of scope**, and remove or reclassify its dependants in the same approved batch. It stays out of
**Decisions so far**.

## Invocation

Two modes. Either way, **never resolve more than one decision per session** — with the exception of research decisions.

### Chart the map

The skill is selected with a loose idea.

1. **Name the destination.** Run `/grill` in its documentation route to pin down what this map is finding its way to — the spec, decision, or change. The destination fixes the scope, so it's settled first.
2. **Map the frontier.** Run `/grill` in its batch route, **breadth-first** this time: fan out across the whole space rather than deep on one thread, surfacing the open decisions and the first steps takeable now. **If this surfaces no fog** — the way to the destination is already clear, the whole journey small enough for one session — you don't need a map. Stop and ask the user how they'd like to proceed.
3. **Draft the map and current decisions.** Fill Destination and Notes, leave Decisions-so-far
   empty, sketch the fog, and draft every decision file that is precise enough now with its status
   and textual blockers.
4. **Preview the charting batch.** Re-read the local tracker and pass the
   [Local write gate](#local-write-gate) with the exact file batch.
5. **Apply and verify the approved batch.** Create `map.md` and decision files in one initiative
   directory, then verify the resulting frontier. If approval is withheld, return the draft only.
6. Research may proceed read-only after charting. Do not create or switch a research branch, write a
   repository report, or write findings to a decision file without the corresponding approval.
   Resolving a research decision uses the same resolution batch below.
7. Stop — charting is one session's work; it hand-resolves nothing.

### Work through the map

The skill is selected with a `map.md` path. A decision path is optional; without one, choose the
first frontier decision.

1. Load the **map** — the low-res view — and read only enough decision files to find the frontier.
2. Choose the named decision or the first frontier decision. Show the exact `Status: claimed`
   change, pass the [Local write gate](#local-write-gate), apply it, and verify the claim. Without
   claim approval, return the candidate and stop.
3. Resolve it — **zoom as needed**: fetch any related or resolved decision file on demand;
   invoke the skills the `## Notes` block names. If in doubt, use `/grill` and choose its one-question
   or batch route.
4. Draft one resolution batch: `## Answer`, `Status: resolved`, the map pointer, newly surfaced
   decision files and blockers, fog changes, scope changes, and any required decision-file update.
5. Re-read every target, show the final batch, and pass the [Local write gate](#local-write-gate).
   Apply and verify only that approved batch. Without approval, return the resolution draft and
   leave files unchanged apart from the separately approved claim.

The user may run unblocked decisions in parallel, so expect other sessions to edit the files.

When the map is clear, stop and hand the same initiative directory to `/to-spec`. Its reading and
drafting may begin automatically, while writing `spec.md` requires separate approval. Do not invoke
`/to-tickets` or `/implement` from the map.
