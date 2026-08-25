package com.example.beyond_may_be.recommendation.service;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
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
public class GroqRecommendationClient {

  private static final Logger log = LoggerFactory.getLogger(GroqRecommendationClient.class);

  private static final String MODEL = "openai/gpt-oss-20b";
  private static final String SYSTEM_PROMPT =
      """
      전달된 후보의 placeId만 최종 할당량에 정확히 맞춰 순서대로 선택하세요.
      카테고리와 태그의 다양성, 유형 혼합을 고려하세요.
      짧은 일정은 주소 기준 근접성을, 긴 일정은 지역 분산을 더 고려하세요.
      제공된 운영시간이 일정 날짜에 적합한지 고려하되 운영시간이 없는 장소는 제외하거나 감점하지 마세요.
      설명, 추천 이유, 장소명, 신규 ID 없이 placeIds만 반환하세요.
      """;

  private final RestClient restClient;
  private final String apiKey;
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public GroqRecommendationClient(
      @Qualifier("groqRestClient") RestClient restClient,
      @Value("${groq.api-key:}") String apiKey) {
    this.restClient = restClient;
    this.apiKey = apiKey;
  }

  public List<Long> rank(RankRequest request) {
    // 5-1. DB 후보와 할당량을 보내고 strict JSON Schema로 placeIds만 응답받는다.
    // 키가 없거나 호출·파싱이 실패하면 빈 목록을 반환해 Service의 전체 규칙 폴백을 사용한다.
    if (apiKey.isBlank()) {
      log.warn("Groq API 키가 없어 규칙 기반 추천을 사용합니다.");
      return List.of();
    }
    try {
      ChatResponse response =
          restClient
              .post()
              .uri("/chat/completions")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(chatRequest(objectMapper.writeValueAsString(request)))
              .retrieve()
              .body(ChatResponse.class);
      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().getFirst().message() == null
          || response.choices().getFirst().message().content() == null) {
        log.warn("Groq 응답 본문이 없어 규칙 기반 추천을 사용합니다.");
        return List.of();
      }
      JsonNode result = objectMapper.readTree(response.choices().getFirst().message().content());
      if (!result.isObject()
          || result.size() != 1
          || !result.has("placeIds")
          || !result.path("placeIds").isArray()) {
        log.warn("Groq 응답 계약 검증에 실패해 규칙 기반 추천을 사용합니다.");
        return List.of();
      }
      List<Long> placeIds = new ArrayList<>();
      for (JsonNode placeId : result.path("placeIds")) {
        if (!placeId.isIntegralNumber() || !placeId.canConvertToLong()) {
          log.warn("Groq 장소 ID 검증에 실패해 규칙 기반 추천을 사용합니다.");
          return List.of();
        }
        placeIds.add(placeId.longValue());
      }
      return List.copyOf(placeIds);
    } catch (JsonProcessingException | RestClientException exception) {
      log.warn(
          "Groq 호출 또는 응답 파싱에 실패해 규칙 기반 추천을 사용합니다. cause={}", exception.getClass().getSimpleName());
      return List.of();
    }
  }

  private ChatRequest chatRequest(String input) {
    Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("placeIds", Map.of("type", "array", "items", Map.of("type", "integer"))),
            "required",
            List.of("placeIds"),
            "additionalProperties",
            false);
    ResponseFormat responseFormat =
        new ResponseFormat("json_schema", new JsonSchema("recommendation_selection", true, schema));
    return new ChatRequest(
        MODEL,
        List.of(new Message("system", SYSTEM_PROMPT), new Message("user", input)),
        responseFormat,
        false);
  }

  public record RankRequest(
      Map<TravelPreferenceType, Integer> scores,
      Map<TravelPreferenceType, Integer> quotas,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      List<Candidate> candidates) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Candidate(
      Long placeId,
      TravelPreferenceType preferenceType,
      String name,
      String category,
      List<String> tags,
      String address,
      String businessHours) {}

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
