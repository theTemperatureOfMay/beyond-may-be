---
name: code-review
description: Read-only review of work-in-progress, branch, PR, or user-pinned changes along separate Standards and Spec axes. Use on explicit review requests and after general or large implementations; small changes only when requested or risk warrants it.
---

Review a bounded change along two independent axes:

- **Standards** — does the code conform to this repo's documented coding standards?
- **Spec** — does the code faithfully implement the originating issue / PRD / spec?

Run both axes as parallel sub-agents when both are available, then aggregate without merging their
priorities. The review is read-only and ends with findings; do not change code, Git, or issue-tracker
state.

## Process

### 1. Resolve the comparison

- For WIP, review HEAD plus staged, unstaged, and relevant untracked changes. Use `git diff HEAD` for
  tracked changes and `git ls-files --others --exclude-standard` to identify untracked candidates; read
  only files relevant to the requested work and never protected files.
- For a branch or PR, compare from the merge-base. Resolve the target/base branch first, then use the
  merge-base through the current working state.
- A user-specified comparison point takes priority over an inferred WIP, branch, or PR base.
- For a supplied commit, tag, or relative ref, validate it with `git rev-parse` and use the comparison the
  user requested.

Ask only when the intended base is genuinely ambiguous. Record the commands and included untracked
files so both review axes inspect the same change.

### 2. Identify the Standards sources

Standards come from AGENTS.md, linked canonical documentation, conventions, ADRs, and existing code
style. Follow links relevant to the changed area, including API or commit conventions when applicable.
Treat documented rules as the authority; do not invent a generic style guide or report formatting issues
that repository tooling already enforces.

### 3. Identify the Spec sources

Spec comes from an approved one-session request or ticket completion criteria; a parent spec or
issue supplies intent and context. Prefer a source the user named, then references in the approved implementation record, branch,
or commits. Use `docs/agents/issue-tracker.md` only to read an already-referenced tracker item; never
invoke setup or mutate the tracker.

If Spec cannot be found, search first, ask once, continue Standards, and report Spec as not verified.
Do not fabricate requirements from the diff.

### 4. Run the two axes

Spawn two parallel review sub-agents when Spec exists. Give both the same comparison commands, included
untracked files, and change scope.

**Standards brief**

- Provide the relevant Standards source paths.
- Find correctness, safety, or maintainability problems introduced by the change and every documented
  rule violation.
- Cite a tight file/line range and the governing rule. Report only actionable findings; do not praise or
  summarize the diff.

**Spec brief**

- Provide the approved one-session request or ticket completion criteria and parent context.
- Find missing or partial requirements, incorrect implementations, and unrequested scope.
- Cite a tight file/line range and the matching requirement.

If Spec is unavailable, run only Standards and mark the other axis as not verified.

### 5. Aggregate

Present findings under `## Standards` and `## Spec`. Order actionable findings by severity within each
axis and state when an axis has no findings or was not verified. Standards and Spec conflicts are
reported separately; never choose between them. End with counts and the worst finding within each axis.

## Why two axes

A change can pass one axis and fail the other:

- Code that follows every standard but implements the wrong thing → **Standards pass, Spec fail.**
- Code that does exactly what the issue asked but breaks the project's conventions → **Spec pass, Standards fail.**

Reporting them separately stops one axis from masking the other.
