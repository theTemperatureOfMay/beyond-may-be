# When to Mock

Use the repository's existing constructor-injection seams. Mockito is appropriate for an established
dependency when the test is about the caller's behavior, not that dependency's implementation.

## Appropriate uses

- a Spring Data repository in a focused Service test;
- an external API client whose network behavior is outside the test;
- time or randomness already exposed through an injected dependency;
- failure responses needed to exercise caller error handling.

```java
@Mock UserRepository userRepository;
@InjectMocks UserService userService;
```

Stub only behavior required by the scenario. Prefer AssertJ assertions on the result or exception.

## Prefer real infrastructure when semantics matter

- Use Testcontainers for PostgreSQL queries, mappings, constraints, and transactions.
- Use MockMvc for Spring MVC routing, validation, serialization, status, and security.
- Use a real value object or domain object instead of mocking simple data.

## Avoid

- mocking the class under test;
- mocking private helpers or static implementation details;
- adding an interface solely for Mockito;
- deep-stub chains that mirror internal navigation;
- verifying every call count or order when an observable outcome is available;
- mixing a mocked repository with assertions that claim real database behavior.

If a required test has no stable seam, do not silently redesign the production API. Apply the stop rule
in `SKILL.md` when a new public interface or structure would be needed.
