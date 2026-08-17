---
name: to-tickets
description: Break an approved spec into tracer-bullet implementation tickets with blocking edges. Use when an approved spec needs executable tickets; reading and drafting may start automatically, while publishing the exact GitHub batch requires separate approval.
---

# To Tickets

Break an approved spec into a set of **implementation tickets** — tracer-bullet vertical slices,
each declaring the tickets that **block** it. If no approved spec is identified, stop and ask for
the source; do not synthesize a replacement spec inside this skill.

This skill drafts and publishes implementation tickets. It does not revise the parent spec,
implement a ticket, commit, or change parent issue state. Use this project's GitHub tracker and
`docs/agents/issue-tracker.md`. If tracker configuration is missing, stop and recommend
`/setup-skills`. It may inspect and draft automatically, but repository or GitHub writes still
require the applicable exact-batch approval.

## Process

### 1. Gather context

Work from the approved spec already identified in the conversation. If the user passes a spec path,
issue number, or URL, fetch it and read its full body and comments. Re-read it before publishing.

### 2. Explore the codebase (optional)

If you have not already explored the codebase, do so to understand the current state of the code. Ticket titles and descriptions should use the project's domain glossary vocabulary, and respect ADRs in the area you're touching.

Identify only prefactoring that is genuinely required for a slice to land green. Represent it as a
separate blocking implementation ticket; this skill does not implement it. Keep small same-scope
cleanup in the affected slice and omit speculative refactors.

### 3. Draft vertical slices

Break the work into **tracer bullet** tickets.

<vertical-slice-rules>

- Each slice cuts a narrow but COMPLETE path through every layer (schema, API, UI, tests) — vertical, NOT a horizontal slice of one layer
- A completed slice is demoable or verifiable on its own
- Each slice is sized to fit in a single fresh context window
- Required prefactoring is a separate blocking implementation ticket; this skill does not implement it

</vertical-slice-rules>

Give each ticket its **blocking edges** — the other tickets that must complete before it can start. A ticket with no blockers can start immediately.
Each complete ticket is a direct `/implement` input once its blockers are resolved; a complete
implementation ticket is not necessarily currently unblocked.

**Wide refactors are the exception to vertical slicing.** A **wide refactor** is one mechanical change — rename a column, retype a shared symbol — whose **blast radius** fans across the whole codebase, so a single edit breaks thousands of call sites at once and no vertical slice can land green. Don't force it into a tracer bullet; sequence it as **expand–contract**. First expand: add the new form beside the old so nothing breaks. Then migrate the call sites over in batches sized by blast radius (per package, per directory), each batch its own ticket blocked by the expand, keeping CI green batch to batch because the old form still exists. Finally contract: delete the old form once no caller remains, in a ticket blocked by every migrate batch. When even the batches can't stay green alone, keep the sequence but let them share an integration branch that all block a final integrate-and-verify ticket — green is promised only there.

### 4. Quiz the user

Present the proposed breakdown as a numbered list. For each ticket, show:

- **Title**: short descriptive name
- **Blocked by**: which other tickets (if any) must complete first
- **What it delivers**: the end-to-end behaviour this ticket makes work

Ask the user:

- Does the granularity feel right? (too coarse / too fine)
- Are the blocking edges correct — does each ticket only depend on tickets that genuinely gate it?
- Should any tickets be merged or split further?

Iterate until the user approves the breakdown.

Breakdown approval does not approve GitHub writes.

### 5. Publish the tickets to GitHub

Before publishing, re-read the parent and tracker state. Show the exact issue bodies, labels, parent
relationships, and dependency edges, plus the expected impact and recovery path. Ask for separate
explicit approval immediately before writing. If any target or content changed since the preview,
show the revised batch and obtain fresh approval.

Publish one GitHub issue per ticket in dependency order, blockers first. Link every ticket as a
sub-issue of the parent spec and create native blocking relationships using
`docs/agents/issue-tracker.md`. Apply `ready-for-agent` only as described below.

Do not edit or close the parent body or state. Apply `ready-for-agent` only to complete
implementation tickets whose body, acceptance criteria, parent relationship, and blocking edges
were included in the approved batch.

<issue-template>

## Parent

A reference to the approved parent spec issue.

## What to build

The end-to-end behaviour this ticket makes work, from the user's perspective — not layer-by-layer implementation.

## Acceptance criteria

- [ ] Criterion 1
- [ ] Criterion 2

## Blocked by

- A reference to each blocking ticket, or "None — can start immediately".

</issue-template>

Avoid specific file paths or code snippets — they go stale fast. Exception: if a prototype produced a snippet that encodes a decision more precisely than prose can (state machine, reducer, schema, type shape), inline it and note briefly that it came from a prototype. Trim to the decision-rich parts — not a working demo, just the important bits.

After verifying the created issues and dependency graph, report the frontier and stop. Do not invoke
`/implement`, modify the parent spec, or close any issue.
