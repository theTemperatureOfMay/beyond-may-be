---
name: codebase-design
description: Read-only guidance for module interfaces, architecture alternatives, and test seams. Use for a concrete design question, not ordinary implementation, routine review, or straightforward bug fixing.
---

# Codebase Design

Use only for module interface, architecture, or test-seam design. The goal is to compare shapes that
hide complexity, keep change local, and expose behavior through a stable test surface.

## Entry and exit

Use this skill when the request concerns at least one of these decisions:

- what callers should know about a module;
- where an observable or replaceable test seam should sit;
- whether several shallow responsibilities should be combined;
- which of materially different interface or architecture alternatives is preferable.

Do not select it for ordinary implementation, routine code review, or an obvious local bug. Deliver a
read-only conversational design analysis and stop; do not create or modify files or code. When this
skill is consulted by another skill, return the design criteria or comparison to that caller and do not
expand the caller's write scope.

## Design criteria

These terms are lenses for analysis, not replacement names for the repository:

- **Module**: a function, class, package, or slice with an interface and implementation.
- **Interface**: everything a caller must know, including inputs, results, invariants, errors, ordering,
  configuration, and relevant performance constraints. It may be an HTTP API, a Java public method, or
  another existing project surface.
- **Depth**: useful behavior hidden behind a smaller interface. Avoid pass-through modules that add
  knowledge without hiding it.
- **Seam**: a place where a caller can observe behavior or replace a dependency without editing the
  calling code. In tests, it answers "what do we call, and what result do we observe?"
- **Leverage**: capability callers gain for the interface they must learn.
- **Locality**: how well knowledge, change, faults, and verification stay together.

Preserve project terms such as Service, API, HTTP, and DDD names in code and canonical documentation.
Do not rename a `UserService`, Controller, API contract, or domain term merely to fit these criteria.

## Deep vs shallow

**Deep module** = small interface + lots of implementation:

```
┌─────────────────────┐
│   Small Interface   │  ← Few methods, simple params
├─────────────────────┤
│                     │
│  Deep Implementation│  ← Complex logic hidden
│                     │
└─────────────────────┘
```

**Shallow module** = large interface + little implementation (avoid):

```
┌─────────────────────────────────┐
│       Large Interface           │  ← Many methods, complex params
├─────────────────────────────────┤
│  Thin Implementation            │  ← Just passes through
└─────────────────────────────────┘
```

When designing an interface, ask:

- Can I reduce the number of methods?
- Can I simplify the parameters?
- Can I hide more complexity inside?

## Principles

- **Depth is a property of the interface, not the implementation.** A deep module can be internally composed of small, mockable, swappable parts — they just aren't part of the interface. A module can have **internal seams** (private to its implementation, used by its own tests) as well as the **external seam** at its interface.
- **The deletion test.** Imagine deleting the module. If complexity vanishes, it was a pass-through. If complexity reappears across N callers, it was earning its keep.
- **The interface is the test surface.** Callers and tests cross the same seam. If you want to test *past* the interface, the module is probably the wrong shape.
- **Prefer an existing seam.** Add a new abstraction only when the production contract or a real
  replacement need justifies it, not solely so a test can mock it.

## Designing for testability

Good interfaces make testing natural:

1. Exercise behavior through an existing public `Service` method, HTTP request, repository contract, or
   library extension point.
2. Follow the repository's constructor-injection style for real dependencies such as repositories and
   external clients.
3. Observe returned values, exceptions, HTTP responses, or persisted outcomes instead of private
   methods and incidental call order.
4. Keep the public surface small, but do not introduce an interface or adapter only for a mock.

## Process

1. Read `AGENTS.md`, the relevant canonical documentation, existing callers, tests, and ADRs.
2. State the design question, constraints, current public surface, and existing test seam.
3. If one answer is clearly sufficient, analyze that answer. If trade-offs are material, compare at
   least two genuinely different alternatives; parallel agents are not required.
4. Compare interface size, hidden complexity, locality, caller migration, error behavior, and the test
   surface.
5. Recommend one option and identify unresolved decisions or validation that a later plan must carry.

## Going deeper

- **Deepening a cluster given its dependencies** — see [DEEPENING.md](DEEPENING.md).
- **Exploring alternative interfaces** — see [DESIGN-IT-TWICE.md](DESIGN-IT-TWICE.md).
