---
name: improve-codebase-architecture
description: User-invoked, read-only scan for architecture improvement candidates. Write one Markdown report under .dev/architecture-review and stop without designing or implementing a candidate.
disable-model-invocation: true
---

# Improve Codebase Architecture

Find architecture improvement candidates and record evidence without changing the architecture. This
skill is user-invoked only.

Use `codebase-design`'s design criteria internally when they help evaluate a candidate, but do not
automatically invoke that skill. Preserve the repository's `Service`, API, HTTP, package, and domain
names. Read `AGENTS.md`, `docs/index.md`, relevant canonical documentation, existing tests, and ADRs.

## Process

### 1. Explore

Use the scope named by the user. If none was named, use recent history and current architecture docs to
choose a bounded area, and state that inferred scope in the report. Read the codebase directly; no
sub-agent or browser is required.

Record file-and-line evidence for questions such as:

- Where does understanding one concept require bouncing between many small modules?
- Where are modules **shallow** — interface nearly as complex as the implementation?
- Where have pure functions been extracted just for testability, but the real bugs hide in how they're called (no **locality**)?
- Where do tightly-coupled modules leak across their seams?
- Which parts of the codebase are untested, or hard to test through their current interface?

Apply the deletion test and distinguish observed friction from a speculative improvement. Do not open
protected files, mutate Git state, or edit tracked files while scanning.

### 2. Write one Markdown report

Read the repository and write exactly one Markdown report under `.dev/architecture-review/` named
`YYMMDD-HHmm-<scope>.md`. Create the directory only when writing the report. Sanitize `<scope>` to a
short lowercase hyphenated slug.

Invocation authorizes only that report; do not modify code or canonical documentation. The report is a
personal work record, not a canonical architecture decision. Use this structure:

```markdown
# Architecture review: <scope>

- Date:
- Scanned scope:
- Evidence limits:

## Candidates
### <candidate>
- Evidence: `path:line`
- Friction:
- Candidate shape:
- Expected benefit:
- Risk and compatibility:
- Existing validation seam:
- Confidence: strong / worth exploring / speculative

## Top recommendation
## Deferred or unresolved
## Suggested next route — not invoked
```

Do not propose a detailed new public interface unless evidence requires it to explain the candidate.
Mark ADR conflicts and unverified assumptions explicitly.

### 3. Stop

Stop after the report. Do not automatically invoke codebase-design, implementation, a browser, or
CDN-based HTML. Return the report path and a compact summary.

The user chooses the next task separately: unresolved interface design can use `codebase-design`, a
clear local change can enter the normal work-classification path, and a large structural or public
contract change can enter `wayfinder`. Recommend a route when useful, but do not invoke it.
