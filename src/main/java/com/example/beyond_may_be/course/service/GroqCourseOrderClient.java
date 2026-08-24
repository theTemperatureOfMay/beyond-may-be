package com.example.beyond_may_be.course.service;

import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GroqCourseOrderClient {

  private static final Logger log = LoggerFactory.getLogger(GroqCourseOrderClient.class);
  private static final String MODEL = "openai/gpt-oss-20b";
  private static final LocalTime DEFAULT_START_TIME = LocalTime.of(7, 0);
  private static final int DEFAULT_STAY_MINUTES = 60;
  private static final String SYSTEM_PROMPT =
      """
      전달된 모든 placeId를 누락과 중복 없이 정확히 한 번씩 사용해 dayCount개의 일정을 만드세요.
      각 날짜의 장소 수 차이는 최대 1개로 맞추고, 현재 장소에서 다음 장소까지 가까운 거리를
      우선해 이동 동선이 짧아지도록 배열하세요. distances의 직선거리(meters)를 기준으로 판단하세요.
      FOODIE 또는 음식 장소는 가능한 경우 점심과 저녁 식사 순서에 배치하고, startTime과
      estimatedStayMinutes 및 운영시간을 고려하세요. 운영시간이 누락된 장소는 제외하거나 감점하지 마세요.
      설명이나 새로운 ID 없이 days만 반환하세요.
      """;

  private final RestClient restClient;
  private final String apiKey;
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public GroqCourseOrderClient(
      @Qualifier("groqRestClient") RestClient restClient,
      @Value("${groq.api-key:}") String apiKey) {
    this.restClient = restClient;
    this.apiKey = apiKey;
  }

  public List<List<Long>> order(OrderRequest request) {
    if (apiKey.isBlank()) {
      log.warn("Groq API 키가 없어 규칙 기반 코스 생성을 사용합니다.");
      return List.of();
    }
    try {
      ChatResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(chatRequest(request))
              .retrieve()
              .body(ChatResponse.class);
      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().getFirst() == null
          || response.choices().getFirst().message() == null
          || response.choices().getFirst().message().content() == null) {
        log.warn("Groq 코스 응답 본문이 없어 규칙 기반 생성을 사용합니다.");
        return List.of();
      }
      JsonNode result = objectMapper.readTree(response.choices().getFirst().message().content());
      if (!result.isObject()
          || result.size() != 1
          || !result.has("days")
          || !result.path("days").isArray()) {
        log.warn("Groq 코스 응답 계약 검증에 실패해 규칙 기반 생성을 사용합니다.");
        return List.of();
      }
      List<List<Long>> days = new ArrayList<>();
      for (JsonNode day : result.path("days")) {
        if (!day.isArray()) {
          return List.of();
        }
        List<Long> placeIds = new ArrayList<>();
        for (JsonNode placeId : day) {
          if (!placeId.isIntegralNumber() || !placeId.canConvertToLong()) {
            return List.of();
          }
          placeIds.add(placeId.longValue());
        }
        days.add(List.copyOf(placeIds));
      }
      return List.copyOf(days);
    } catch (JsonProcessingException | RestClientException exception) {
      log.warn(
          "Groq 코스 호출 또는 파싱에 실패해 규칙 기반 생성을 사용합니다. cause={}", exception.getClass().getSimpleName());
      return List.of();
    }
  }

  private ChatRequest chatRequest(OrderRequest request) throws JsonProcessingException {
    Map<String, Object> daySchema =
        Map.of("type", "array", "items", Map.of("type", "integer"), "minItems", 1);
    Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "days",
                Map.of(
                    "type",
                    "array",
                    "items",
                    daySchema,
                    "minItems",
                    request.dayCount(),
                    "maxItems",
                    request.dayCount())),
            "required",
            List.of("days"),
            "additionalProperties",
            false);
    ResponseFormat responseFormat =
        new ResponseFormat("json_schema", new JsonSchema("course_order", true, schema));
    return new ChatRequest(
        MODEL,
        List.of(
            new Message("system", SYSTEM_PROMPT),
            new Message("user", objectMapper.writeValueAsString(promptInput(request)))),
        responseFormat,
        false);
  }

  private PromptInput promptInput(OrderRequest request) {
    List<Candidate> candidates =
        request.places().stream()
            .map(
                place ->
                    new Candidate(
                        place.getId(),
                        place.getName(),
                        place.getCategory(),
                        place.getTravelMbtiType(),
                        place.getTags(),
                        place.getAddress(),
                        place.getLatitude(),
                        place.getLongitude(),
                        place.getBusinessHours()))
            .toList();
    List<Distance> distances = new ArrayList<>();
    for (int from = 0; from < request.places().size(); from++) {
      for (int to = from + 1; to < request.places().size(); to++) {
        Place first = request.places().get(from);
        Place second = request.places().get(to);
        distances.add(
            new Distance(
                first.getId(),
                second.getId(),
                Math.round(
                    GeoDistanceCalculator.distanceMeters(
                        first.getLatitude().doubleValue(),
                        first.getLongitude().doubleValue(),
                        second.getLatitude().doubleValue(),
                        second.getLongitude().doubleValue()))));
      }
    }
    return new PromptInput(
        request.travelSchedule(),
        request.startDate(),
        request.endDate(),
        request.dayCount(),
        DEFAULT_START_TIME,
        DEFAULT_STAY_MINUTES,
        candidates,
        distances);
  }

  public record OrderRequest(
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      int dayCount,
      List<Place> places) {}

  private record PromptInput(
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      int dayCount,
      LocalTime startTime,
      int estimatedStayMinutes,
      List<Candidate> places,
      List<Distance> distances) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record Candidate(
      Long placeId,
      String name,
      String category,
      TravelPreferenceType preferenceType,
      List<String> tags,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String businessHours) {}

  private record Distance(Long fromPlaceId, Long toPlaceId, long meters) {}

  private record ChatRequest(
      String model,
      List<Message> messages,
      @JsonProperty("response_format") ResponseFormat responseFormat,
      boolean stream) {}

  private record Message(String role, String content) {}

  private record ResponseFormat(String type, @JsonProperty("json_schema") JsonSchema jsonSchema) {}

  private record JsonSchema(String name, boolean strict, Map<String, Object> schema) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ChatResponse(List<Choice> choices) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Choice(MessageContent message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MessageContent(String content) {}
}
