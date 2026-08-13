---
name: ask-matt
description: Ask which repository skill or work route fits the current request. Use when the user wants help choosing a lifecycle stage, not when they already named a skill or approved input to execute.
disable-model-invocation: true
---

# Ask Matt

You don't remember every skill, so ask.

This skill is a router only. Recommend a route and stop. It does not invoke another skill or
create a plan, spec, ticket, code change, commit, or external write. Return the route, why it fits,
its expected read/write scope, and the next approval the user must give.

A **flow** is a path through the skills. Most paths run along one **main flow**, and two **on-ramps** merge onto it. Everything else is standalone, or a vocabulary layer that runs underneath.

## GitHub-linked user-invoked skills

`/wayfinder`, `/to-spec`, `/to-tickets`, `/triage`, `/setup-skills`,
`/gh-create-issue-from-template`, and `/gh-create-project-pr` can read or write GitHub and are
user-invoked. This router may only recommend one: state the exact skill, why it fits, and its expected
read/write scope, ask whether to invoke it, then stop. A user request that names the skill or a later
explicit yes is invocation approval.

Invocation approval starts only reading, classifying, and drafting. Before any GitHub write, the
invoked skill must show the exact final target and change batch and obtain separate approval.

## The main flow: idea → ship

The route most work travels. You have an idea and want it built.

1. **`/grill`** — sharpen the idea with questions. Choose its one-question or batch route, and add the documentation route when resolved terms or decisions should be recorded in `CONTEXT.md` or ADRs.
2. **Branch — can you settle every question in conversation?** If a question needs a runnable answer (state, business logic, a UI you have to see), detour through a prototype, bridged by **`/handoff`** in both directions (see Crossing sessions):
   - **`/handoff`** out, then open a fresh session against that file,
   - **`/prototype`** to answer the question with throwaway code,
   - **`/handoff`** back what you learned, and reference it from the original idea thread.
3. **Classify the work with `AGENTS.md`.** Session length and file count do not choose the route.
   - **Small work** → recommend the direct approval path; do not add `plan` or `implement`.
   - **General implementation** → recommend **`/plan`** and stop. Its separate approval is the
     entry gate for **`/implement`**.
   - **Large work** → recommend **`/wayfinder`** and ask for invocation approval. When its decision
     map is clear and its `Notes` did not carry the destination through execution, recommend
     **`/to-spec`**, then **`/to-tickets`**, each with separate invocation and GitHub-write approval,
     before **`/implement`**.
   - **An approved plan or ticket named for implementation** → recommend **`/implement`**; do not
     recreate earlier lifecycle artifacts.
   - **A parent spec named without an implementation ticket** → recommend **`/to-tickets`** and ask
     for invocation approval. A spec supplies context; it is not the unit `/implement` executes.

`wayfinder` owns the long-lived decision map, `to-spec` one parent implementation target,
`to-tickets` executable child tickets, and `implement` repository changes. Do not collapse those
responsibilities into this router.

### Context hygiene

Use `/handoff` when a later stage needs a fresh session. The router does not start that stage or
carry out any work on its behalf.

## On-ramps

A starting situation that generates work, then merges onto the main flow.

- **Bugs and requests piling up** → recommend **`/triage`** and ask for invocation approval. Once approved, it reads incoming issues, drafts triage roles and briefs, and applies separately approved tracker changes to produce agent-ready issues, which **`/implement`** later picks up.

  Triage is only for issues **you didn't create** — bug reports, incoming feature requests, anything that arrives raw. Tickets that `/to-tickets` produced are already agent-ready, so **don't triage them**.

- **Something's broken** → **`/diagnosing-bugs`**. For the hard ones: the bug that resists a first glance, the intermittent flake, the regression that crept in between two known-good states. It refuses to theorise until it has a **tight feedback loop** — one command that already goes red on *this* bug — then records the evidence, cause or uncertainty, and recommended fix in `.dev`. It stops at the diagnosis report. A fix and permanent regression test begin only from a separate user request; hand off an architectural seam finding to **`/improve-codebase-architecture`** only then.

- **A huge, foggy effort — a greenfield project or a huge feature build, too big for one session** → recommend **`/wayfinder`**, explain its scope, and ask for invocation approval. Once approved, it charts a **shared map** of **decision tickets** — producing **decisions, not deliverables** — until the fog is pushed back and the way is clear. Reading and drafting need no further approval; before any issue-tracker or other external write it shows the exact final batch and applies only what the user separately approves. Where **`/grill`** sharpens an idea you can hold in one session, wayfinder is for the idea you can't — and it's slower and denser, so save it for exactly that, never a well-scoped feature.

  Decision work is the default. A map may carry execution tasks only when its `Notes` explicitly
  opts into them and names their scope; the usual repository and external-write approvals still apply.

  When the map clears without completing the destination under its `Notes` execution override,
  **it hands off**: recommend **`/to-spec`** and ask for invocation approval so it can collapse the
  map's linked decisions into a buildable target, then separately recommend `/to-tickets` before
  `/implement`. If the approved `Notes` already carried the destination through verified execution,
  report that outcome and stop instead of creating redundant lifecycle artifacts.

## Codebase health

Not feature work — upkeep.

- **`/improve-codebase-architecture`** — run whenever you have a spare moment to keep the codebase good for agents to operate in. It surfaces **deepening opportunities**; picking one _generates an idea_ you can take into the main flow at `/grill`. It's the survey that finds the candidates; **`/codebase-design`** (below) is the bench you design the chosen one on.

## Vocabulary underneath

Two model-invoked references that run *beneath* the other skills — each the single source of truth for its vocabulary. Reach for them directly when the **words**, not the process, are the problem; or let the skills above pull them in.

- **`/domain-modeling`** — sharpen the project's *domain* language: challenge a fuzzy term, resolve an overloaded word ("account" doing three jobs), record a hard-to-reverse decision as an ADR. It's the active discipline the documentation route of `/grill` uses to keep `CONTEXT.md` a clean glossary.
- **`/codebase-design`** — the deep-module vocabulary (module, interface, depth, seam, adapter, leverage, locality) for designing a module's *shape*: a lot of behaviour behind a small interface at a clean seam. `/tdd` and `/improve-codebase-architecture` both speak it.

## Crossing sessions

- **`/handoff`** — when a thread is full or you need to branch off (e.g. into a `/prototype` session), this compacts the conversation into a markdown file. You don't continue in place — you **open a new session and reference that file** to carry the context across. It's the bridge between context windows, in either direction. Use it when you want a **fresh session** but need the **current conversation preserved**.
- **`/compact`** (built-in) — stay in the **same conversation**, letting the earlier turns be summarized. Use it at **intentional breaks between phases**, when you don't mind losing the verbatim history. Don't compact mid-phase — the agent can lose its way. `/handoff` forks; `/compact` continues.

## Standalone

Off the main flow entirely.

- **`/prototype`** — a small, throwaway program that answers one design question: does this state model feel right, or what should this UI look like. Throwaway from day one — keep the answer, delete the code. It's the detour in step 2 of the main flow, but reach for it any time a design question is hard to settle on paper.
- **`/research`** — delegate reading legwork to a **background agent**: it investigates a question against **primary sources**, then leaves a cited Markdown file in the repo. Keep working while it reads. The file it produces is something to take *into* the main flow at `/grill` — research feeds the thinking, it doesn't replace it.
- **`/teach`** — learn a concept over multiple sessions, using the current directory as a stateful workspace.
- **`/writing-great-skills`** — reference for writing and editing skills well.

## Precondition

Recommend **`/setup-skills`** before the first engineering flow when the issue tracker, triage labels,
or document layout is not configured, then ask for invocation approval. Custom issue trackers also
work.
