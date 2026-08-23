# Deepening

Use this guide when several shallow responsibilities may be combined behind an existing project
surface. It applies the design criteria in [SKILL.md](SKILL.md) without renaming Spring or domain
concepts.

## Read the real path first

Trace the Controller, Service, Converter, repository/client, DTO, tests, and relevant canonical
documentation before proposing a merge. Identify which public methods, HTTP contracts, transactions,
errors, and persistence rules callers already depend on.

## Dependency cases

### In-process behavior

For calculation or state transitions without I/O, keep the observable public method and move scattered
rules behind it. Verify known examples with JUnit and AssertJ.

### PostgreSQL and repositories

Do not replace production persistence semantics with an in-memory substitute merely for convenience.
Use the existing repository injection seam for Service tests and Testcontainers when SQL, mappings,
constraints, or transactions are part of the behavior.

### HTTP and external clients

Preserve the existing client or API contract. Mockito can isolate an established injected client in a
focused Service test; an integration test should use the repository's existing HTTP test pattern when
serialization, security, or status codes matter. Do not add a new port solely for a test double.

### Framework extension points

Spring configuration, servlet filters, Logback layouts, converters, and similar extension points are
already seams when callers invoke their public framework contract. Test that contract rather than a
private helper.

## Safe deepening

1. Name the caller-visible behavior that must remain unchanged.
2. Choose an existing public seam whenever it can observe that behavior.
3. Compare the current and proposed responsibility placement, caller migration, transaction/error
   behavior, and validation cost.
4. Keep old tests until equivalent behavior is demonstrably covered at the new seam; delete only true
   duplication.
5. If the change creates a new public interface or restructures a load-bearing boundary, stop at the
   design recommendation and route the work through the repository's approval flow.
