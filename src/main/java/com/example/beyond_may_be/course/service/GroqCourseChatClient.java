package com.example.beyond_may_be.course.service;

import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.place.domain.Place;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Groq(OpenAI 호환) chat completions API로 코스 챗봇(AI 코스 수정 요청)을 처리한다. API 키가 없거나 호출·응답 파싱이 실패하면 빈 값을
 * 반환한다. 이 기능에는 방문 순서 최적화(GroqCourseOrderClient)와 달리 대체할 알고리즘이 없으므로, 호출부가 빈 값을 오류로 취급한다.
 */
@Slf4j
@Component
public class GroqCourseChatClient {

  private static final String CHAT_COMPLETIONS_PATH = "/openai/v1/chat/completions";
  static final String TYPE_COURSE_REVISION = "COURSE_REVISION";
  static final String TYPE_ADD_RECOMMENDATION = "ADD_RECOMMENDATION";
  private static final int MAX_ATTEMPTS = 2;
  private static final String JSON_ARRAY_FORMAT_NOTE =
      "days의 각 일자는 place id를 콤마로 구분한 하나의 문자열이어야 한다(숫자 배열이 아니라 "
          + "문자열이다). 예를 들어 두 일자, 첫째 날 장소 3개(id 12, 45, 7), 둘째 날 장소 2개(id "
          + "30, 9)라면 \"days\": [\"12,45,7\", \"30,9\"]로 응답하라. 각 문자열 안에서 id 사이에는 "
          + "반드시 콤마를 넣어라 — id를 그냥 이어 붙여 하나의 숫자처럼 보이게 쓰면 안 된다(예: "
          + "\"1245\"는 잘못된 형식이다).";
  private static final String DISTANCE_MATRIX_NOTE =
      "장소 쌍 사이의 실제 이동 거리(미터 단위)는 이미 계산되어 distancesMeters 필드로 제공된다"
          + "(distancesMeters[fromId][toId] 형식, 예: distancesMeters[103][101] = 520이면 103과 "
          + "101 사이가 520미터). 위도·경도를 보고 직접 거리를 암산하지 말고, 순서를 정할 때 반드시 "
          + "이 distancesMeters 값을 사용하라.";
  private static final String SYSTEM_PROMPT =
      "너는 여행 코스를 사용자 요청에 맞게 다듬어주는 광주 관광 도우미다. 현재 코스의 일자별 장소 목록과 "
          + "사용자의 자유 텍스트 요청, 그리고 추가로 추천할 수 있는 후보 장소 목록(candidatePlaces)을 "
          + "받는다. "
          + DISTANCE_MATRIX_NOTE
          + " 사용자가 순서 변경이나 코스 흐름 개선을 원하면 "
          + "{\"type\": \"COURSE_REVISION\", \"message\": \"<한국어 응답>\", "
          + "\"days\": [[placeId, ...], [placeId, ...]]} 형식으로 응답하라. days는 입력받은 "
          + "일자 수와 같은 개수의 배열이어야 하고, 입력받은 모든 place id를 정확히 한 번씩만 "
          + "포함해야 한다(장소를 추가하거나 빼지 않는다). "
          + JSON_ARRAY_FORMAT_NOTE
          + " 사용자가 새로운 장소 추가를 원하면 "
          + "{\"type\": \"ADD_RECOMMENDATION\", \"message\": \"<한국어 응답>\", "
          + "\"recommendations\": [{\"placeId\": <candidatePlaces에 있는 id>, "
          + "\"reason\": \"<추천 이유>\"}]} 형식으로 응답하라. placeId는 반드시 candidatePlaces에 "
          + "있는 id만 사용해야 하며, 다른 id를 지어내지 마라. 반드시 이 두 형식 중 하나의 JSON "
          + "객체만 응답하고 다른 설명은 포함하지 마라.";
  private static final String PLACEMENT_SYSTEM_PROMPT =
      "너는 여행 코스에 새 장소 하나를 자연스럽게 끼워 넣어 전체 동선을 다시 정리하는 광주 관광 "
          + "도우미다. 현재 코스의 일자별 장소 목록(각 장소는 id를 갖는다), 새로 추가할 장소 정보, "
          + "그리고 expectedPlaceCount(최종 결과에 포함되어야 할 place id의 총 개수)를 받는다. "
          + DISTANCE_MATRIX_NOTE
          + " 이 거리 값을 사용해 새 장소를 가장 적절한 일자와 순서에 삽입하고, 전체 방문 순서를 "
          + "다시 정리하라. "
          + "{\"days\": [[placeId, ...], [placeId, ...]]} 형식의 JSON 객체만 "
          + "응답하라. days에 등장하는 place id를 모두 합친 개수는 반드시 expectedPlaceCount와 "
          + "정확히 같아야 하고, 입력으로 받은 기존 장소 id 전체와 새 장소 id를 정확히 한 번씩만 "
          + "포함해야 한다 — 어떤 장소도 빠뜨리거나 중복하거나 지어내면 안 된다. "
          + JSON_ARRAY_FORMAT_NOTE
          + " 응답을 만든 뒤 days에 포함된 id 개수를 다시 세어 expectedPlaceCount와 일치하는지 "
          + "스스로 확인하라. 다른 설명은 포함하지 마라.";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;

  @Autowired
  public GroqCourseChatClient(
      @Value("${groq.api-key:}") String apiKey,
      @Value("${groq.model}") String model,
      @Value("${groq.timeout-seconds:60}") int timeoutSeconds) {
    this(buildRestClient(timeoutSeconds), new ObjectMapper(), apiKey, model);
  }

  GroqCourseChatClient(
      RestClient restClient, ObjectMapper objectMapper, String apiKey, String model) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.model = model;
  }

  private static RestClient buildRestClient(int timeoutSeconds) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    return RestClient.builder()
        .baseUrl("https://api.groq.com")
        .requestFactory(requestFactory)
        .build();
  }

  public Optional<ChatSuggestion> requestRevision(
      Course course,
      List<CoursePlace> currentPlaces,
      Map<Long, Place> placesById,
      List<Place> candidatePlaces,
      String userMessage) {
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        String responseBody =
            restClient
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    buildRequestBody(
                        course, currentPlaces, placesById, candidatePlaces, userMessage))
                .retrieve()
                .body(String.class);

        ChatSuggestion suggestion = parseSuggestion(responseBody);
        if (suggestion == null) {
          log.warn(
              "[시도 {}/{}] Groq 챗봇 응답을 해석할 수 없어 재시도합니다. Groq 원본 응답={}",
              attempt,
              MAX_ATTEMPTS,
              responseBody);
          continue;
        }
        if (TYPE_COURSE_REVISION.equals(suggestion.type())
            && !isValidRevision(suggestion.days(), currentPlaces)) {
          log.warn(
              "[시도 {}/{}] Groq 챗봇 응답이 요청한 장소 집합과 일치하지 않아 재시도합니다. Groq 원본 응답={}",
              attempt,
              MAX_ATTEMPTS,
              responseBody);
          continue;
        }
        if (TYPE_ADD_RECOMMENDATION.equals(suggestion.type())
            && !isValidRecommendation(suggestion.recommendations(), candidatePlaces)) {
          log.warn(
              "[시도 {}/{}] Groq 챗봇이 후보 목록 밖의 장소를 추천해 재시도합니다. Groq 원본 응답={}",
              attempt,
              MAX_ATTEMPTS,
              responseBody);
          continue;
        }
        return Optional.of(suggestion);
      } catch (Exception e) {
        log.warn("[시도 {}/{}] Groq 챗봇 요청이 실패했습니다.", attempt, MAX_ATTEMPTS, e);
      }
    }
    return Optional.empty();
  }

  public Optional<List<List<Long>>> requestPlacementRevision(
      Course course, List<CoursePlace> currentPlaces, Map<Long, Place> placesById, Place newPlace) {
    if (apiKey == null || apiKey.isBlank()) {
      return Optional.empty();
    }
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        String responseBody =
            restClient
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildPlacementRequestBody(course, currentPlaces, placesById, newPlace))
                .retrieve()
                .body(String.class);

        List<List<Long>> days = parsePlacementDays(responseBody);
        if (days == null) {
          log.warn(
              "[시도 {}/{}] Groq 삽입 재배치 응답을 해석할 수 없어 재시도합니다. Groq 원본 응답={}",
              attempt,
              MAX_ATTEMPTS,
              responseBody);
          continue;
        }
        if (!isValidPlacement(days, currentPlaces, newPlace)) {
          Set<Long> expectedIds =
              currentPlaces.stream().map(CoursePlace::getPlaceId).collect(Collectors.toSet());
          expectedIds.add(newPlace.getId());
          log.warn(
              "[시도 {}/{}] Groq 삽입 재배치 응답이 기존 장소 + 새 장소 집합과 일치하지 않아 재시도합니다. "
                  + "기대 placeId 집합={}, Groq 원본 응답={}",
              attempt,
              MAX_ATTEMPTS,
              expectedIds,
              responseBody);
          continue;
        }
        return Optional.of(days);
      } catch (Exception e) {
        log.warn("[시도 {}/{}] Groq 삽입 재배치 요청이 실패했습니다.", attempt, MAX_ATTEMPTS, e);
      }
    }
    return Optional.empty();
  }

  private Map<String, Object> buildPlacementRequestBody(
      Course course, List<CoursePlace> currentPlaces, Map<Long, Place> placesById, Place newPlace) {
    return Map.of(
        "model", model,
        "response_format", Map.of("type", "json_object"),
        "messages",
            List.of(
                Map.of("role", "system", "content", PLACEMENT_SYSTEM_PROMPT),
                Map.of(
                    "role",
                    "user",
                    "content",
                    buildPlacementUserPrompt(course, currentPlaces, placesById, newPlace))));
  }

  private String buildPlacementUserPrompt(
      Course course, List<CoursePlace> currentPlaces, Map<Long, Place> placesById, Place newPlace) {
    Map<Integer, List<Map<String, Object>>> dayGroups = new LinkedHashMap<>();
    for (CoursePlace coursePlace : currentPlaces) {
      Place place = placesById.get(coursePlace.getPlaceId());
      dayGroups
          .computeIfAbsent(coursePlace.getDayNumber(), key -> new ArrayList<>())
          .add(
              Map.of(
                  "id", place.getId(),
                  "name", place.getName(),
                  "category", place.getCategory(),
                  "latitude", place.getLatitude(),
                  "longitude", place.getLongitude()));
    }
    Map<String, Object> newPlacePayload =
        Map.of(
            "id", newPlace.getId(),
            "name", newPlace.getName(),
            "category", newPlace.getCategory(),
            "latitude", newPlace.getLatitude(),
            "longitude", newPlace.getLongitude());
    List<Place> allPlaces = new ArrayList<>();
    for (CoursePlace coursePlace : currentPlaces) {
      allPlaces.add(placesById.get(coursePlace.getPlaceId()));
    }
    allPlaces.add(newPlace);
    Map<String, Object> payload =
        Map.of(
            "travelSchedule", course.getTravelSchedule().name(),
            "days", dayGroups.values(),
            "newPlace", newPlacePayload,
            "distancesMeters", buildDistanceMatrixMeters(allPlaces),
            "expectedPlaceCount", currentPlaces.size() + 1);
    return writeJson(payload);
  }

  /** place id 쌍마다 Haversine 실제 거리(미터, 반올림)를 계산한 대칭 행렬을 만든다. */
  private Map<Long, Map<Long, Long>> buildDistanceMatrixMeters(List<Place> places) {
    Map<Long, Map<Long, Long>> matrix = new LinkedHashMap<>();
    for (Place from : places) {
      Map<Long, Long> row = new LinkedHashMap<>();
      for (Place to : places) {
        if (!from.getId().equals(to.getId())) {
          long meters =
              Math.round(
                  GeoDistanceCalculator.distanceMeters(
                      from.getLatitude().doubleValue(),
                      from.getLongitude().doubleValue(),
                      to.getLatitude().doubleValue(),
                      to.getLongitude().doubleValue()));
          row.put(to.getId(), meters);
        }
      }
      matrix.put(from.getId(), row);
    }
    return matrix;
  }

  private List<List<Long>> parsePlacementDays(String responseBody) throws Exception {
    JsonNode root = objectMapper.readTree(responseBody);
    String content = root.at("/choices/0/message/content").asText();
    JsonNode contentNode = objectMapper.readTree(content);
    JsonNode daysNode = contentNode.path("days");
    if (!daysNode.isArray()) {
      return null;
    }
    List<List<Long>> days = new ArrayList<>();
    for (JsonNode dayNode : daysNode) {
      days.add(parseDayIds(dayNode));
    }
    return days;
  }

  /**
   * 하루치 place id 목록을 파싱한다. 모델이 지시대로 콤마로 구분된 문자열("103,101,2")을 주면 그것을 분리해서 파싱하고, 혹시 숫자 JSON 배열([103,
   * 101, 2])로 응답해도 그대로 지원한다. 콤마 구분 없이 숫자를 이어 붙인 경우(예: "1031012")는 여기서 걸러지지 않고 그대로 하나의 큰 숫자로 파싱되며,
   * 이후 집합 검증(isValidRevision/isValidPlacement) 단계에서 개수 불일치로 걸러진다.
   */
  private List<Long> parseDayIds(JsonNode dayNode) {
    List<Long> ids = new ArrayList<>();
    if (dayNode.isTextual()) {
      for (String token : dayNode.asText().split(",")) {
        String trimmed = token.trim();
        if (!trimmed.isEmpty()) {
          ids.add(Long.parseLong(trimmed));
        }
      }
    } else if (dayNode.isArray()) {
      dayNode.forEach(idNode -> ids.add(idNode.asLong()));
    }
    return ids;
  }

  private boolean isValidPlacement(
      List<List<Long>> days, List<CoursePlace> currentPlaces, Place newPlace) {
    Set<Long> expected =
        currentPlaces.stream().map(CoursePlace::getPlaceId).collect(Collectors.toSet());
    expected.add(newPlace.getId());
    Set<Long> actual = new HashSet<>();
    int total = 0;
    for (List<Long> day : days) {
      actual.addAll(day);
      total += day.size();
    }
    return total == expected.size() && actual.equals(expected);
  }

  private Map<String, Object> buildRequestBody(
      Course course,
      List<CoursePlace> currentPlaces,
      Map<Long, Place> placesById,
      List<Place> candidatePlaces,
      String userMessage) {
    return Map.of(
        "model", model,
        "response_format", Map.of("type", "json_object"),
        "messages",
            List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of(
                    "role",
                    "user",
                    "content",
                    buildUserPrompt(
                        course, currentPlaces, placesById, candidatePlaces, userMessage))));
  }

  private String buildUserPrompt(
      Course course,
      List<CoursePlace> currentPlaces,
      Map<Long, Place> placesById,
      List<Place> candidatePlaces,
      String userMessage) {
    Map<Integer, List<Map<String, Object>>> dayGroups = new LinkedHashMap<>();
    for (CoursePlace coursePlace : currentPlaces) {
      Place place = placesById.get(coursePlace.getPlaceId());
      dayGroups
          .computeIfAbsent(coursePlace.getDayNumber(), key -> new ArrayList<>())
          .add(
              Map.of(
                  "id", place.getId(),
                  "name", place.getName(),
                  "category", place.getCategory(),
                  "latitude", place.getLatitude(),
                  "longitude", place.getLongitude()));
    }
    List<Map<String, Object>> candidatePayload =
        candidatePlaces.stream()
            .map(
                place ->
                    Map.<String, Object>of(
                        "id", place.getId(),
                        "name", place.getName(),
                        "category", place.getCategory(),
                        "tags", place.getTags(),
                        "latitude", place.getLatitude(),
                        "longitude", place.getLongitude()))
            .toList();
    List<Place> currentPlaceEntities =
        currentPlaces.stream()
            .map(coursePlace -> placesById.get(coursePlace.getPlaceId()))
            .toList();
    Map<String, Object> payload =
        Map.of(
            "travelSchedule", course.getTravelSchedule().name(),
            "days", dayGroups.values(),
            "distancesMeters", buildDistanceMatrixMeters(currentPlaceEntities),
            "candidatePlaces", candidatePayload,
            "userMessage", userMessage);
    return writeJson(payload);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Groq 챗봇 요청 페이로드 직렬화에 실패했습니다.", e);
    }
  }

  private ChatSuggestion parseSuggestion(String responseBody) throws Exception {
    JsonNode root = objectMapper.readTree(responseBody);
    String content = root.at("/choices/0/message/content").asText();
    JsonNode contentNode = objectMapper.readTree(content);
    String type = contentNode.path("type").asText(null);
    String message = contentNode.path("message").asText("");

    if (TYPE_COURSE_REVISION.equals(type)) {
      List<List<Long>> days = new ArrayList<>();
      for (JsonNode dayNode : contentNode.path("days")) {
        days.add(parseDayIds(dayNode));
      }
      return new ChatSuggestion(type, message, days, List.of());
    }
    if (TYPE_ADD_RECOMMENDATION.equals(type)) {
      List<Recommendation> recommendations = new ArrayList<>();
      contentNode
          .path("recommendations")
          .forEach(
              recNode ->
                  recommendations.add(
                      new Recommendation(
                          recNode.path("placeId").asLong(), recNode.path("reason").asText(""))));
      return new ChatSuggestion(type, message, List.of(), recommendations);
    }
    return null;
  }

  private boolean isValidRevision(List<List<Long>> days, List<CoursePlace> currentPlaces) {
    Set<Long> expected =
        currentPlaces.stream().map(CoursePlace::getPlaceId).collect(Collectors.toSet());
    Set<Long> actual = new HashSet<>();
    int total = 0;
    for (List<Long> day : days) {
      actual.addAll(day);
      total += day.size();
    }
    return total == expected.size() && actual.equals(expected);
  }

  private boolean isValidRecommendation(
      List<Recommendation> recommendations, List<Place> candidatePlaces) {
    Set<Long> candidateIds = candidatePlaces.stream().map(Place::getId).collect(Collectors.toSet());
    return recommendations.stream().allMatch(rec -> candidateIds.contains(rec.placeId()));
  }

  public record ChatSuggestion(
      String type, String message, List<List<Long>> days, List<Recommendation> recommendations) {}

  public record Recommendation(Long placeId, String reason) {}
}
