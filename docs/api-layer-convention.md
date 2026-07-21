# API 계층 코딩 컨벤션

## 적용 대상

이 문서는 `src/main/java`의 도메인별 API 계층에 적용한다.
새 API를 만들거나 기존 API 계층을 수정할 때는 기존 코드 스타일과 함께 이 규칙을 따른다.

## 계층 구조

```text
Controller
  -> Service
    -> Converter
      -> Response DTO
```

- 호출은 위 방향으로만 흐른다.
- Controller는 Entity를 직접 변환하거나 도메인 로직을 수행하지 않는다.
- Converter는 Repository나 Service에 의존하지 않는다.

## Controller

- HTTP 경로, 요청 값 바인딩, 응답 상태만 담당한다.
- 도메인 로직, Repository 조회, Entity 변환을 직접 수행하지 않는다.
- 성공 응답은 다음 형태로 반환한다.

```java
return ApiResponse.onSuccess(preferenceTestService.getQuestions());
```

- Service는 인터페이스가 아닌 구체 클래스를 직접 주입한다.

## Service

- `@Service`를 붙인 구체 클래스로 작성한다.
- Repository 조회, 도메인 데이터 조합, 처리 순서를 담당한다.
- Entity 또는 도메인 데이터는 Converter를 통해 Response DTO로 변환하여 반환한다.
- Service 인터페이스와 `Impl` 클래스는 여러 구현체가 필요한 경우에만 도입한다.

```java
return PreferenceTestConverter.toQuestionsResponse(questions);
```

## Converter

- `final` 클래스와 `private` 생성자를 사용한다.
- 상태를 갖지 않는 `static` 변환 메서드만 둔다.
- Repository 접근, 랜덤 추출, 권한 판단, 예외 발생 등의 도메인 처리를 포함하지 않는다.
- 메서드 이름은 `toXxxResponse`처럼 변환 결과를 드러낸다.

## DTO

- 도메인별 `XxxDtos` 최상위 클래스로 Request와 Response 타입을 묶는다.
- 최상위 클래스는 `final`로 만들고 인스턴스 생성을 막는다.
- API Request와 Response는 기본적으로 Java `record`를 사용한다.
- GET API에 요청 값이 없으면 빈 Request DTO를 만들지 않는다.
- Response 필드명과 타입은 API 명세를 그대로 따른다.

```java
public record QuestionsResponse(
    List<QuestionResponse> questions,
    int totalCount
) {}
```

## 예외와 미구현 처리

- 실제 도메인 오류는 프로젝트의 공통 예외 처리 체계를 사용한다.
- API 스켈레톤 단계에서 아직 구현되지 않은 조회 지점은 `UnsupportedOperationException`으로 명시할 수 있다.
- 스켈레톤의 미구현 예외는 실제 API 구현 시 공통 예외 처리 체계로 교체한다.

