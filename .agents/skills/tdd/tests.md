# Good and Bad Tests

Follow the existing test package, naming, fixture, and assertion style before introducing a new one.

## Service behavior

Use the public Service method. An injected repository may be a Mockito mock when persistence semantics
are not the behavior under test.

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock UserRepository userRepository;
  @InjectMocks UserService userService;

  @DisplayName("회원가입에 성공한다.")
  @Test
  void signUp() {
    UserSignUpRequestDto request =
        new UserSignUpRequestDto("testuser", null, null, null, null);
    User savedUser = User.builder().nickname("testuser").identificationCode(1).build();
    given(userRepository.existsByNicknameAndIdentificationCode(anyString(), anyInt()))
        .willReturn(false);
    given(userRepository.save(any(User.class))).willReturn(savedUser);

    UserSignUpResponseDto response = userService.signUp(request);

    assertThat(response.getNickname()).isEqualTo("testuser");
    assertThat(response.getIdentificationCode()).isNotNull();
  }
}
```

The assertion describes caller-visible behavior. Verify a repository call only when that interaction is
itself the contract and no outcome can express it.

## HTTP behavior

Use MockMvc when routing, validation, serialization, status, or security is the behavior.

```java
mockMvc.perform(post("/api/users")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json))
    .andExpect(status().isBadRequest());
```

Do not call a Controller private helper or reproduce Spring validation logic in the expected value.

## PostgreSQL behavior

Use the existing Testcontainers setup when SQL, mapping, constraints, or transaction behavior matters.
An in-memory database is not equivalent evidence for PostgreSQL-specific behavior.

## Red flags

- calling private methods through reflection;
- asserting incidental call order or count instead of an outcome;
- recomputing the expected value with the same algorithm as production code;
- adding a new interface only to make a mock possible;
- using a full Spring context when a focused public-method test proves the same behavior;
- replacing a PostgreSQL behavior test with an in-memory substitute.
