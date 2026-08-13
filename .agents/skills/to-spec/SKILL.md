---
name: to-spec
description: Synthesize a resolved conversation or cleared wayfinder map into exactly one parent GitHub spec issue. Use only after explicit invocation approval; publishing requires separate final-batch approval.
disable-model-invocation: true
---

# To Spec

This skill takes the current conversation context and codebase understanding and produces a spec (you may know this document as a PRD). Do NOT interview the user — just synthesize what you already know.

This skill creates exactly one parent spec issue. It does not create an implementation plan,
implementation tickets, code changes, commits, or issue-state transitions. Invocation approval does
not approve publishing. Ask for separate explicit approval immediately before writing.

Use this project's GitHub issue tracker and `docs/agents/issue-tracker.md`. If the tracker or label
mapping is missing, stop and recommend `/setup-skills` with its expected read/write scope and ask
for invocation approval; do not invoke it automatically.

## Process

1. Explore the repo to understand the current state of the codebase, if you haven't already. Use the project's domain glossary vocabulary throughout the spec, and respect any ADRs in the area you're touching.

2. Sketch out the seams at which you're going to test the feature. Existing seams should be preferred to new ones. Use the highest seam possible. If new seams are needed, propose them at the highest point you can. The fewer seams across the codebase, the better - the ideal number is one.

Check with the user that these seams match their expectations.

3. Write the spec using the template below. Re-read the target, then show the target repository,
   final title, full body, labels, expected impact, and recovery path. Ask for explicit approval for
   that exact GitHub write batch.

   The issue records the intended implementation target. Determine the
   current implemented state from code and canonical documentation.

4. After that separate approval, create exactly one parent spec issue and verify its title, body,
   labels, and URL. Do not apply `ready-for-agent` to the parent spec; `/to-tickets` owns complete
   implementation tickets and their readiness labels.

5. Stop after reporting the verified issue. When implementation is intended, recommend
   `/to-tickets` with its expected read/write scope; it may produce one or more implementation
   tickets. Do not invoke it.

<spec-template>

## Problem Statement

The problem that the user is facing, from the user's perspective.

## Solution

The solution to the problem, from the user's perspective.

## User Stories

A LONG, numbered list of user stories. Each user story should be in the format of:

1. As an <actor>, I want a <feature>, so that <benefit>

<user-story-example>
1. As a mobile bank customer, I want to see balance on my accounts, so that I can make better informed decisions about my spending
</user-story-example>

This list of user stories should be extremely extensive and cover all aspects of the feature.

## Implementation Decisions

A list of implementation decisions that were made. This can include:

- The modules that will be built/modified
- The interfaces of those modules that will be modified
- Technical clarifications from the developer
- Architectural decisions
- Schema changes
- API contracts
- Specific interactions

Do NOT include specific file paths or code snippets. They may end up being outdated very quickly.

Exception: if a prototype produced a snippet that encodes a decision more precisely than prose can (state machine, reducer, schema, type shape), inline it within the relevant decision and note briefly that it came from a prototype. Trim to the decision-rich parts — not a working demo, just the important bits.

## Testing Decisions

A list of testing decisions that were made. Include:

- A description of what makes a good test (only test external behavior, not implementation details)
- Which modules will be tested
- Prior art for the tests (i.e. similar types of tests in the codebase)

## Out of Scope

A description of the things that are out of scope for this spec.

## Further Notes

Any further notes about the feature.

</spec-template>
