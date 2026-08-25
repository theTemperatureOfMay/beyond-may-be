package com.example.beyond_may_be.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class GroqRecommendationClientTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private MockRestServiceServer server;
  private GroqRecommendationClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.groq.test/openai/v1");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new GroqRecommendationClient(builder.build(), "test-key");
  }

  @Test
  void sendsOnlyAllowedCandidateDataWithStrictJsonSchemaAndParsesOrderedIds() {
    server
        .expect(once(), requestTo("https://api.groq.test/openai/v1/chat/completions"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
        .andExpect(
            request -> {
              JsonNode body =
                  objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
              assertThat(body.path("model").asText()).isEqualTo("openai/gpt-oss-20b");
              assertThat(body.path("stream").asBoolean()).isFalse();
              assertThat(body.path("response_format").path("type").asText())
                  .isEqualTo("json_schema");
              JsonNode jsonSchema = body.path("response_format").path("json_schema");
              assertThat(jsonSchema.path("strict").asBoolean()).isTrue();
              assertThat(jsonSchema.path("schema").path("additionalProperties").asBoolean())
                  .isFalse();
              assertThat(jsonSchema.path("schema").path("required").get(0).asText())
                  .isEqualTo("placeIds");

              String systemPrompt = body.path("messages").get(0).path("content").asText();
              assertThat(systemPrompt).contains("카테고리", "태그", "근접", "분산", "운영시간", "감점", "유형");

              JsonNode input =
                  objectMapper.readTree(body.path("messages").get(1).path("content").asText());
              assertThat(input.path("scores").path("THINKER").asInt()).isEqualTo(4);
              assertThat(input.path("quotas").path("FOODIE").asInt()).isEqualTo(6);
              assertThat(input.path("travelSchedule").asText()).isEqualTo("DAY_TRIP");
              assertThat(input.path("startDate").asText()).isEqualTo("2099-08-20");
              assertThat(input.path("endDate").asText()).isEqualTo("2099-08-20");
              assertThat(input.has("userId")).isFalse();
              assertThat(input.has("nickname")).isFalse();
              assertThat(input.has("identificationCode")).isFalse();
              assertThat(input.has("token")).isFalse();

              JsonNode firstCandidate = input.path("candidates").get(0);
              assertThat(firstCandidate.properties())
                  .extracting(Map.Entry::getKey)
                  .containsExactlyInAnyOrder(
                      "placeId",
                      "preferenceType",
                      "name",
                      "category",
                      "tags",
                      "address",
                      "businessHours");
              JsonNode secondCandidate = input.path("candidates").get(1);
              assertThat(secondCandidate.has("address")).isFalse();
              assertThat(secondCandidate.has("businessHours")).isFalse();
            })
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\\"placeIds\\\":[2,1]}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    List<Long> result = client.rank(request());

    assertThat(result).containsExactly(2L, 1L);
    server.verify();
  }

  @Test
  void convertsErrorResponseToEmptyResult() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(withRawStatus(500));

    assertThat(client.rank(request())).isEmpty();
    server.verify();
  }

  @Test
  void convertsMalformedJsonToEmptyResult() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));

    assertThat(client.rank(request())).isEmpty();
    server.verify();
  }

  @Test
  void rejectsAResponseWithFieldsOutsideThePlaceIdsContract() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\\"placeIds\\\":[1],\\\"reason\\\":\\\"설명\\\"}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.rank(request())).isEmpty();
    server.verify();
  }

  @Test
  void convertsTimeoutToEmptyResultWithoutRetrying() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(
            request -> {
              throw new ResourceAccessException("timeout");
            });

    assertThat(client.rank(request())).isEmpty();
    server.verify();
  }

  @Test
  void doesNotCallGroqWhenKeyIsMissing() {
    client = new GroqRecommendationClient(RestClient.create(), "");

    assertThat(client.rank(request())).isEmpty();
  }

  private GroqRecommendationClient.RankRequest request() {
    Map<TravelPreferenceType, Integer> scores =
        Map.of(
            TravelPreferenceType.THINKER, 4,
            TravelPreferenceType.FOODIE, 3,
            TravelPreferenceType.ARTIST, 2,
            TravelPreferenceType.REMEMBERER, 1);
    Map<TravelPreferenceType, Integer> quotas =
        Map.of(
            TravelPreferenceType.THINKER, 8,
            TravelPreferenceType.FOODIE, 6,
            TravelPreferenceType.ARTIST, 4,
            TravelPreferenceType.REMEMBERER, 2);
    List<GroqRecommendationClient.Candidate> candidates =
        List.of(
            new GroqRecommendationClient.Candidate(
                1L,
                TravelPreferenceType.THINKER,
                "장소 1",
                "자연 관광",
                List.of("산책"),
                "광주 동구",
                "매일 09:00~18:00"),
            new GroqRecommendationClient.Candidate(
                2L, TravelPreferenceType.FOODIE, "장소 2", "음식", List.of("한식"), null, null));
    return new GroqRecommendationClient.RankRequest(
        scores,
        quotas,
        TravelSchedule.DAY_TRIP,
        LocalDate.of(2099, 8, 20),
        LocalDate.of(2099, 8, 20),
        candidates);
  }
}
