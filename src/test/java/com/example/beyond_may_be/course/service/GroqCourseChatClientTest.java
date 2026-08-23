package com.example.beyond_may_be.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GroqCourseChatClientTest {

  private static final String CHAT_COMPLETIONS_URL =
      "https://api.groq.com/openai/v1/chat/completions";

  private Place placeAt(long id, String name) {
    Place place =
        Place.builder()
            .name(name)
            .category("카페")
            .travelMbtiType(TravelPreferenceType.THINKER)
            .address("광주")
            .latitude(BigDecimal.valueOf(35.00))
            .longitude(BigDecimal.valueOf(126.85))
            .businessHours("09:00-18:00")
            .description("설명")
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private Place placeAtCoordinate(long id, String name, double lat, double lon) {
    Place place =
        Place.builder()
            .name(name)
            .category("카페")
            .travelMbtiType(TravelPreferenceType.THINKER)
            .address("광주")
            .latitude(BigDecimal.valueOf(lat))
            .longitude(BigDecimal.valueOf(lon))
            .businessHours("09:00-18:00")
            .description("설명")
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private Course draftCourse() {
    Course course =
        Course.builder()
            .ownerUserId(1L)
            .title("광주 여행")
            .status(CourseStatus.DRAFT)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .startTime(LocalTime.of(9, 0))
            .aiRevisionCount(0)
            .build();
    ReflectionTestUtils.setField(course, "id", 10L);
    return course;
  }

  private CoursePlace coursePlaceAt(long placeId, int day, int order) {
    return CoursePlace.builder()
        .courseId(10L)
        .placeId(placeId)
        .dayNumber(day)
        .visitOrder(order)
        .estimatedStayMinutes(60)
        .travelModeFromPrevious(order == 1 ? null : "WALK")
        .build();
  }

  private GroqCourseChatClient clientWithMockServer(
      MockRestServiceServer[] serverHolder, String apiKey) {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.groq.com");
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    return new GroqCourseChatClient(builder.build(), new ObjectMapper(), apiKey, "test-model");
  }

  private String chatCompletionResponse(String content) {
    String escapedContent = content.replace("\"", "\\\"");
    return "{\"choices\": [{\"message\": {\"content\": \"" + escapedContent + "\"}}]}";
  }

  @DisplayName("COURSE_REVISION 응답(콤마 구분 문자열 형식)을 유효하게 파싱한다.")
  @Test
  void requestRevision_courseRevisionResponse_parsesDays() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response =
        chatCompletionResponse(
            "{\"type\": \"COURSE_REVISION\", \"message\": \"야경 명소를 앞쪽에 배치했어요\", "
                + "\"days\": [\"3,1,2\"]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    Place placeA = placeAt(1L, "A");
    Place placeB = placeAt(2L, "B");
    Place placeC = placeAt(3L, "C");
    List<CoursePlace> currentPlaces =
        List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2), coursePlaceAt(3L, 1, 3));
    Map<Long, Place> placesById = Map.of(1L, placeA, 2L, placeB, 3L, placeC);

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "야경 명소 넣어줘");

    assertThat(result).isPresent();
    assertThat(result.get().type()).isEqualTo("COURSE_REVISION");
    assertThat(result.get().days()).containsExactly(List.of(3L, 1L, 2L));
  }

  @DisplayName("COURSE_REVISION 응답이 예전 숫자 배열 형식이어도 계속 지원한다(하위 호환).")
  @Test
  void requestRevision_courseRevisionResponse_legacyArrayFormat_stillParses() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response =
        chatCompletionResponse(
            "{\"type\": \"COURSE_REVISION\", \"message\": \"변경\", \"days\": [[3, 1, 2]]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces =
        List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2), coursePlaceAt(3L, 1, 3));
    Map<Long, Place> placesById =
        Map.of(1L, placeAt(1L, "A"), 2L, placeAt(2L, "B"), 3L, placeAt(3L, "C"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "야경 명소 넣어줘");

    assertThat(result).isPresent();
    assertThat(result.get().days()).containsExactly(List.of(3L, 1L, 2L));
  }

  @DisplayName("ADD_RECOMMENDATION 응답이 후보 목록 안의 placeId를 가리키면 유효하게 파싱한다.")
  @Test
  void requestRevision_addRecommendationResponse_parsesRecommendations() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response =
        chatCompletionResponse(
            "{\"type\": \"ADD_RECOMMENDATION\", \"message\": \"카페를 추가해보세요\", "
                + "\"recommendations\": [{\"placeId\": 5, \"reason\": \"조용한 로컬 카페입니다\"}]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));
    List<Place> candidatePlaces = List.of(placeAt(5L, "양림동 카페"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, candidatePlaces, "카페 한 곳 추가");

    assertThat(result).isPresent();
    assertThat(result.get().type()).isEqualTo("ADD_RECOMMENDATION");
    assertThat(result.get().recommendations()).hasSize(1);
    assertThat(result.get().recommendations().get(0).placeId()).isEqualTo(5L);
    assertThat(result.get().recommendations().get(0).reason()).isEqualTo("조용한 로컬 카페입니다");
  }

  @DisplayName("ADD_RECOMMENDATION 응답이 후보 목록 밖의 placeId를 가리키면 재시도 후에도 빈 값을 반환한다.")
  @Test
  void requestRevision_addRecommendationOutsideCandidates_returnsEmpty() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response =
        chatCompletionResponse(
            "{\"type\": \"ADD_RECOMMENDATION\", \"message\": \"카페를 추가해보세요\", "
                + "\"recommendations\": [{\"placeId\": 999, \"reason\": \"할루시네이션\"}]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));
    List<Place> candidatePlaces = List.of(placeAt(5L, "양림동 카페"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, candidatePlaces, "카페 한 곳 추가");

    assertThat(result).isEmpty();
  }

  @DisplayName("COURSE_REVISION 응답의 placeId 집합이 기존과 다르면 재시도 후에도 빈 값을 반환한다.")
  @Test
  void requestRevision_courseRevisionMismatchesPlaces_returnsEmpty() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response =
        chatCompletionResponse(
            "{\"type\": \"COURSE_REVISION\", \"message\": \"변경\", \"days\": [[1, 999]]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"), 2L, placeAt(2L, "B"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "순서 바꿔줘");

    assertThat(result).isEmpty();
  }

  @DisplayName("알 수 없는 type 값이면 재시도 후에도 빈 값을 반환한다.")
  @Test
  void requestRevision_unknownType_returnsEmpty() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response = chatCompletionResponse("{\"type\": \"UNKNOWN\", \"message\": \"?\"}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "아무거나");

    assertThat(result).isEmpty();
  }

  @DisplayName("Groq 호출이 계속 실패하면 재시도 후에도 빈 값을 반환한다.")
  @Test
  void requestRevision_serverError_returnsEmpty() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    serverHolder[0].expect(requestTo(CHAT_COMPLETIONS_URL)).andRespond(withServerError());
    serverHolder[0].expect(requestTo(CHAT_COMPLETIONS_URL)).andRespond(withServerError());

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "아무거나");

    assertThat(result).isEmpty();
  }

  @DisplayName("첫 시도가 무효해도 재시도에서 유효한 응답을 받으면 그 결과를 사용한다.")
  @Test
  void requestRevision_firstAttemptInvalid_secondAttemptValid_returnsSecondResult() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String invalidResponse =
        chatCompletionResponse(
            "{\"type\": \"COURSE_REVISION\", \"message\": \"변경\", \"days\": [[1, 999]]}");
    String validResponse =
        chatCompletionResponse(
            "{\"type\": \"COURSE_REVISION\", \"message\": \"변경\", \"days\": [[2, 1]]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(invalidResponse, MediaType.APPLICATION_JSON));
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(validResponse, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"), 2L, placeAt(2L, "B"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "순서 바꿔줘");

    assertThat(result).isPresent();
    assertThat(result.get().days()).containsExactly(List.of(2L, 1L));
  }

  @DisplayName("API 키가 없으면 호출 없이 빈 값을 반환한다.")
  @Test
  void requestRevision_blankApiKey_returnsEmptyWithoutCalling() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "");

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "아무거나");

    assertThat(result).isEmpty();
    serverHolder[0].verify();
  }

  @DisplayName("삽입 재배치 응답이 기존 장소 전체 + 새 장소를 정확히 포함하면 유효하게 파싱한다.")
  @Test
  void requestPlacementRevision_validResponse_parsesDays() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response = chatCompletionResponse("{\"days\": [\"1,4,2,3\"]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces =
        List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2), coursePlaceAt(3L, 1, 3));
    Map<Long, Place> placesById =
        Map.of(1L, placeAt(1L, "A"), 2L, placeAt(2L, "B"), 3L, placeAt(3L, "C"));
    Place newPlace = placeAt(4L, "D");

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isPresent();
    assertThat(result.get()).containsExactly(List.of(1L, 4L, 2L, 3L));
  }

  @DisplayName("삽입 재배치 응답이 예전 숫자 배열 형식이어도 계속 지원한다(하위 호환).")
  @Test
  void requestPlacementRevision_legacyArrayFormat_stillParses() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response = chatCompletionResponse("{\"days\": [[1, 4, 2, 3]]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces =
        List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2), coursePlaceAt(3L, 1, 3));
    Map<Long, Place> placesById =
        Map.of(1L, placeAt(1L, "A"), 2L, placeAt(2L, "B"), 3L, placeAt(3L, "C"));
    Place newPlace = placeAt(4L, "D");

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isPresent();
    assertThat(result.get()).containsExactly(List.of(1L, 4L, 2L, 3L));
  }

  @DisplayName("삽입 재배치 응답 집합이 기존+신규 장소와 다르면 재시도 후에도 빈 값을 반환한다.")
  @Test
  void requestPlacementRevision_mismatchedSet_returnsEmpty() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String response = chatCompletionResponse("{\"days\": [[1, 2, 999]]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"), 2L, placeAt(2L, "B"));
    Place newPlace = placeAt(4L, "D");

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isEmpty();
  }

  @DisplayName("삽입 재배치 호출이 계속 실패하면 재시도 후에도 빈 값을 반환한다.")
  @Test
  void requestPlacementRevision_serverError_returnsEmpty() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    serverHolder[0].expect(requestTo(CHAT_COMPLETIONS_URL)).andRespond(withServerError());
    serverHolder[0].expect(requestTo(CHAT_COMPLETIONS_URL)).andRespond(withServerError());

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));
    Place newPlace = placeAt(4L, "D");

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isEmpty();
  }

  @DisplayName(
      "첫 시도에서 콤마 없이 숫자가 붙어버린 응답을 받아도(예: 1031012102) 재시도에서 유효한 응답을 받으면 그 결과를 사용한다.")
  @Test
  void requestPlacementRevision_firstAttemptMalformed_secondAttemptValid_returnsSecondResult() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    String malformedResponse = chatCompletionResponse("{\"days\": [[1031012102]]}");
    String validResponse = chatCompletionResponse("{\"days\": [[103, 101, 4, 102]]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(malformedResponse, MediaType.APPLICATION_JSON));
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andRespond(withSuccess(validResponse, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces =
        List.of(
            coursePlaceAt(103L, 1, 1), coursePlaceAt(101L, 1, 2), coursePlaceAt(102L, 1, 3));
    Map<Long, Place> placesById =
        Map.of(103L, placeAt(103L, "A"), 101L, placeAt(101L, "B"), 102L, placeAt(102L, "C"));
    Place newPlace = placeAt(4L, "D");

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isPresent();
    assertThat(result.get()).containsExactly(List.of(103L, 101L, 4L, 102L));
  }

  @DisplayName("삽입 재배치는 API 키가 없으면 호출 없이 빈 값을 반환한다.")
  @Test
  void requestPlacementRevision_blankApiKey_returnsEmptyWithoutCalling() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "");

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, placeAt(1L, "A"));
    Place newPlace = placeAt(4L, "D");

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isEmpty();
    serverHolder[0].verify();
  }

  @DisplayName("삽입 재배치 요청 본문에 장소 간 실제 거리(미터, Haversine 계산)가 포함된다.")
  @Test
  void requestPlacementRevision_includesComputedDistanceMatrixInRequestBody() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    Place existingPlace = placeAtCoordinate(1L, "A", 35.00, 126.85);
    Place newPlace = placeAtCoordinate(4L, "D", 35.01, 126.85);
    long expectedMeters =
        Math.round(GeoDistanceCalculator.distanceMeters(35.00, 126.85, 35.01, 126.85));
    String response = chatCompletionResponse("{\"days\": [\"1,4\"]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andExpect(content().string(containsString("distancesMeters")))
        .andExpect(content().string(containsString(String.valueOf(expectedMeters))))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1));
    Map<Long, Place> placesById = Map.of(1L, existingPlace);

    Optional<List<List<Long>>> result =
        client.requestPlacementRevision(draftCourse(), currentPlaces, placesById, newPlace);

    assertThat(result).isPresent();
    serverHolder[0].verify();
  }

  @DisplayName("COURSE_REVISION 요청 본문에도 장소 간 실제 거리(미터)가 포함된다.")
  @Test
  void requestRevision_includesComputedDistanceMatrixInRequestBody() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GroqCourseChatClient client = clientWithMockServer(serverHolder, "test-api-key");
    Place placeA = placeAtCoordinate(1L, "A", 35.00, 126.85);
    Place placeB = placeAtCoordinate(2L, "B", 35.02, 126.85);
    long expectedMeters =
        Math.round(GeoDistanceCalculator.distanceMeters(35.00, 126.85, 35.02, 126.85));
    String response =
        chatCompletionResponse(
            "{\"type\": \"COURSE_REVISION\", \"message\": \"변경\", \"days\": [\"1,2\"]}");
    serverHolder[0]
        .expect(requestTo(CHAT_COMPLETIONS_URL))
        .andExpect(content().string(containsString("distancesMeters")))
        .andExpect(content().string(containsString(String.valueOf(expectedMeters))))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

    List<CoursePlace> currentPlaces = List.of(coursePlaceAt(1L, 1, 1), coursePlaceAt(2L, 1, 2));
    Map<Long, Place> placesById = Map.of(1L, placeA, 2L, placeB);

    Optional<GroqCourseChatClient.ChatSuggestion> result =
        client.requestRevision(
            draftCourse(), currentPlaces, placesById, List.of(), "순서 바꿔줘");

    assertThat(result).isPresent();
    serverHolder[0].verify();
  }
}
