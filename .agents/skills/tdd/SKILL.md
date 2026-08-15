---
name: tdd
description: Test-driven implementation for approved work or explicit test-first requests. Use existing public seams, stop only for material validation or public-structure decisions, and run Red-Green-behavior-preserving cleanup.
---

# Test-Driven Development

TDD implements one observable behavior at a time. It applies to code whose behavior needs a durable
test; documentation and simple settings use only the relevant validation.

When exploring the codebase, read `CONTEXT.md` (if it exists) so test names and interface vocabulary match the project's domain language, and respect ADRs in the area you're touching.

## What a good test is

Tests verify behavior through public interfaces, not implementation details. Code can change entirely; tests shouldn't. A good test reads like a specification — "user can checkout with valid cart" tells you exactly what capability exists — and survives refactors because it doesn't care about internal structure.

See [tests.md](tests.md) for Java/Spring examples and [mocking.md](mocking.md) for repository-specific
mocking guidance.

## Seams — where tests go

A **seam** is the public surface where a test drives behavior and observes a result without reaching into
private implementation.

- For an approved plan or ticket at an existing seam, do not ask for seam approval again.
- When a clear existing public seam is not recorded, choose it and record it.
- Stop and ask only if test choices materially change validation scope or require a new public interface
  or structure.

Typical repository seams are a `Service` public method with an injected repository, an HTTP request
through MockMvc, a PostgreSQL integration path through Testcontainers, or a library's public extension
method. A new interface is not required merely because Mockito is used.

## Anti-patterns

- **Implementation-coupled** — tests private methods or asserts incidental collaborator order/count when
  caller-visible behavior is sufficient. Mocking an established injected repository or external client
  is allowed; prefer outcome assertions over interaction assertions.
- **Tautological** — the assertion recomputes the expected value the way the code does
  (`assertThat(add(a, b)).isEqualTo(a + b)`), so it passes by construction and can never disagree with
  the code. Expected values must come from an independent source of truth: a known-good literal, a
  worked example, or the Spec.
- **Horizontal slicing** — writing all tests first, then all implementation. Bulk tests verify _imagined_ behavior: you test the _shape_ of things rather than user-facing behavior, the tests go insensitive to real changes, and you commit to test structure before understanding the implementation. Work in **vertical slices** instead — one test → one implementation → repeat, each test a **tracer bullet** that responds to what the last cycle taught you.

## Rules of the loop

Cycle: Red -> Green -> behavior-preserving cleanup.

1. **Red.** Add one behavior-focused test and run the narrowest command. Confirm it fails for the
   intended missing or incorrect behavior, not setup or environment noise.
2. **Green.** Write only the implementation needed for that test and run it again.
3. **Behavior-preserving cleanup.** Remove duplication and improve names or placement without changing
   behavior; keep the focused test green.
4. Repeat vertically for the next behavior. Do not write every test before implementation.

Use JUnit 5, AssertJ, Mockito, MockMvc, Testcontainers, and Gradle according to the existing test style.
On Windows, prefer `.\gradlew.bat test --tests "<fully.qualified.TestName>"` for the focused cycle, then
run the broader commands required by the approved plan.
