---
name: ask-matt
description: Ask which repository skill or work route fits the current request. Use when the user wants help choosing a lifecycle stage, not when they already named a skill or approved input to execute.
---

# Ask Matt

You don't remember every skill, so ask.

This skill is a router only. Recommend a route and stop. It does not invoke another skill or
create a spec, ticket, code change, commit, or external write. Return the route, why it fits,
its expected read/write scope, and the next approval the user must give.

A **flow** is a path through the skills. Most paths run along one **main flow**, and two **on-ramps** merge onto it. Everything else is standalone, or a vocabulary layer that runs underneath.

## Automatically readable routes

`/wayfinder`, `/to-spec`, `/to-tickets`, and `/triage` may automatically read
`.dev/initiatives/` and repository context and draft local Markdown changes. Before changing a
tracker file, the selected skill shows the exact paths and final content and obtains the approval
its own gate requires.

`/gh-create-issue-from-template` and `/gh-create-project-pr` may automatically read GitHub and
draft external changes. Any actual GitHub write still requires its separate final-batch approval.

This router only recommends one route with its read/write scope and next approval, then stops.

## The main flow: idea → ship

The route most work travels. You have an idea and want it built.

1. **`/grill`** — sharpen the idea with its documentation route: `grill-with-docs` for one-question
   work or `batch-grill-with-docs` for a batch. Record resolved terms or durable decisions in
   `CONTEXT.md`, canonical docs, or ADRs as that route requires.
2. **Branch — can you settle every question in conversation?** If a question needs a runnable answer (state, business logic, a UI you have to see), detour through a one-off experiment using the current tool's native session fork (see Crossing sessions):
   - fork the current or saved conversation,
   - build the minimum throwaway artifact that answers only that question,
   - return to the original session and reference the resulting artifact, diff, or canonical record.
3. **Choose by continuity after the questions are resolved.** Use `AGENTS.md` risk classification
   for safety and validation, not to choose where lifecycle records live.
   - **The implementation fits one agent session** → recommend **`/implement`** and stop. It must
     still show the implementation scope and obtain the approval `AGENTS.md` requires.
   - **The implementation spans sessions** → recommend **`/to-spec`** and stop. After its approved
     local `spec.md`, recommend **`/to-tickets`**; after its approved ticket batch, select the
     lowest-numbered ready ticket whose blockers are resolved and recommend **`/implement`**.
   - **The multi-session effort is still foggy** → recommend **`/wayfinder`** and stop. When its
     decision map is clear, it joins the same flow at **`/to-spec`**.
   - **An implementation ticket with unresolved local Markdown blockers** → report the blockers and stop;
     do not recommend `implement`.
   - **A parent spec named without an implementation ticket** → recommend **`/to-tickets`** and
     stop. A spec supplies context; it is not the unit `/implement` executes.

`wayfinder` owns the long-lived decision map, `to-spec` one parent implementation target,
`to-tickets` executable child tickets, and `implement` repository changes. Do not collapse those
responsibilities into this router.

### Context hygiene

Use the current tool's native resume or continue feature to return to a saved session, and its native
fork feature when a later stage needs a fresh branch. Keep multi-session context in
`.dev/initiatives/`, and move decisions that must persist into ADRs or canonical docs. The router does not start that
stage or carry out any work on its behalf.

## On-ramps

A starting situation that generates work, then merges onto the main flow.

- **A Local Markdown implementation ticket's readiness is unclear** → recommend **`/triage`** and
  stop. When selected, it reads that configured ticket and its siblings, assesses its current state,
  blockers, and frontier, and drafts tracker changes. It applies only changes separately approved
  immediately before writing and does not create implementation approval.

  Only the current lowest-numbered `ready-for-agent` ticket whose blockers are resolved may go to
  **`/implement`**, and only after the user confirms.

- **Something's broken** → **`/diagnosing-bugs`**. For the hard ones: the bug that resists a first glance, the intermittent flake, the regression that crept in between two known-good states. It refuses to theorise until it has a **tight feedback loop** — one command that already goes red on *this* bug — then records the evidence, cause or uncertainty, and recommended fix in `.dev`. It stops at the diagnosis report. A fix and permanent regression test begin only from a separate user request; hand off an architectural seam finding to **`/improve-codebase-architecture`** only then.

- **A huge, foggy effort — a greenfield project or a huge feature build, too big for one session** → recommend **`/wayfinder`**, explain its scope, and stop. When selected, it charts a **shared local Markdown map** of **decision files** — producing **decisions, not deliverables** — until the fog is pushed back and the way is clear. Reading and drafting need no approval; before changing the configured tracker files it shows the exact batch and applies only what the user approves. Where **`/grill`** sharpens an idea you can hold in one session, wayfinder is for the idea you cannot.

  The map produces decisions only. When it clears, **it always hands off**: recommend
  **`/to-spec`** so it can collapse the linked decisions into a buildable target, then separately
  recommend `/to-tickets` before `/implement`.

## Codebase health

Not feature work — upkeep.

- **`/improve-codebase-architecture`** — run whenever you have a spare moment to keep the codebase good for agents to operate in. It surfaces **deepening opportunities**; picking one _generates an idea_ you can take into the main flow at `/grill`. It's the survey that finds the candidates; **`/codebase-design`** (below) is the bench you design the chosen one on.

## Vocabulary underneath

Two model-invoked references that run *beneath* the other skills — each the single source of truth for its vocabulary. Reach for them directly when the **words**, not the process, are the problem; or let the skills above pull them in.

- **`/domain-modeling`** — sharpen the project's *domain* language: challenge a fuzzy term, resolve an overloaded word ("account" doing three jobs), record a hard-to-reverse decision as an ADR. It's the active discipline the documentation route of `/grill` uses to keep `CONTEXT.md` a clean glossary.
- **`/codebase-design`** — the deep-module vocabulary (module, interface, depth, seam, adapter, leverage, locality) for designing a module's *shape*: a lot of behaviour behind a small interface at a clean seam. `/tdd` and `/improve-codebase-architecture` both speak it.

## Crossing sessions

- **Native resume or continue** — reload a saved conversation when you want to keep working in the same thread.
- **Native fork** — branch from the current or saved conversation when an experiment needs a fresh session while preserving the original transcript.
- **`/compact`** (built-in) — stay in the **same conversation**, letting the earlier turns be summarized. Use it at **intentional breaks between phases**, when you don't mind losing the verbatim history. Don't compact mid-phase — the agent can lose its way.

## Standalone

Off the main flow entirely.

- **`/research`** — delegate reading legwork to a **background agent**: it investigates a question against **primary sources**, then leaves a cited Markdown file in the repo. Keep working while it reads. The file it produces is something to take *into* the main flow at `/grill` — research feeds the thinking, it doesn't replace it.
- **`/teach-me`** — learn a concept over multiple sessions, using the current directory as a stateful workspace.
- **Creating or updating a project skill** — recommend the current tool's official authoring skill:
  Codex system `skill-creator`, or Claude Code `/skill-creator:skill-creator`. Also apply
  `AGENTS.md`; the official procedure does not replace the repository's approval and safety rules.
- **`/writing-great-skills`** — a Matt Pocock reference for writing and editing skills. It may be
  selected automatically when relevant, but do not use it as the default authoring route.
