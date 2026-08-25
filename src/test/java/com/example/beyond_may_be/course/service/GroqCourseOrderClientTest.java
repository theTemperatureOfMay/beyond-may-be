package com.example.beyond_may_be.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GroqCourseOrderClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private MockRestServiceServer server;
  private GroqCourseOrderClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.groq.test/openai/v1");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new GroqCourseOrderClient(builder.build(), "test-key");
  }

  @Test
  void sendsDistanceAndMealContextWithStrictSchemaAndParsesDays() {
    server
        .expect(once(), requestTo("https://api.groq.test/openai/v1/chat/completions"))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
        .andExpect(
            request -> {
              JsonNode body =
                  objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
              assertThat(body.path("model").asText()).isEqualTo("openai/gpt-oss-20b");
              assertThat(body.path("response_format").path("type").asText())
                  .isEqualTo("json_schema");
              JsonNode schema = body.path("response_format").path("json_schema");
              assertThat(schema.path("strict").asBoolean()).isTrue();
              assertThat(schema.path("schema").path("additionalProperties").asBoolean()).isFalse();

              String systemPrompt = body.path("messages").get(0).path("content").asText();
              assertThat(systemPrompt).contains("가까운 거리", "점심", "저녁", "운영시간", "누락");
              JsonNode input =
                  objectMapper.readTree(body.path("messages").get(1).path("content").asText());
              assertThat(input.path("dayCount").asInt()).isEqualTo(2);
              assertThat(input.path("startTime").asText()).isEqualTo("07:00:00");
              assertThat(input.path("places")).hasSize(3);
              assertThat(input.path("distances")).hasSize(3);
              assertThat(input.path("places").get(1).path("preferenceType").asText())
                  .isEqualTo("FOODIE");
              assertThat(input.path("places").get(1).path("businessHours").asText())
                  .isEqualTo("11:00-21:00");
            })
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\\"days\\\":[[1,2],[3]]}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    List<List<Long>> result = client.order(request());

    assertThat(result).containsExactly(List.of(1L, 2L), List.of(3L));
    server.verify();
  }

  @Test
  void convertsNullChoiceToEmptyResult() {
    server
        .expect(once(), requestTo("https://api.groq.test/openai/v1/chat/completions"))
        .andRespond(withSuccess("{\"choices\":[null]}", MediaType.APPLICATION_JSON));

    assertThat(client.order(request())).isEmpty();
    server.verify();
  }

  private GroqCourseOrderClient.OrderRequest request() {
    return new GroqCourseOrderClient.OrderRequest(
        TravelSchedule.ONE_NIGHT_TWO_DAYS,
        LocalDate.of(2099, 8, 20),
        LocalDate.of(2099, 8, 21),
        2,
        List.of(
            place(1L, 35.10, TravelPreferenceType.THINKER, "09:00-18:00"),
            place(2L, 35.11, TravelPreferenceType.FOODIE, "11:00-21:00"),
            place(3L, 35.20, TravelPreferenceType.ARTIST, null)));
  }

  private Place place(
      long id, double latitude, TravelPreferenceType preferenceType, String businessHours) {
    Place place =
        Place.builder()
            .name("장소 " + id)
            .category(preferenceType == TravelPreferenceType.FOODIE ? "음식" : "관광")
            .travelMbtiType(preferenceType)
            .tags(List.of("태그"))
            .address("광주")
            .latitude(BigDecimal.valueOf(latitude))
            .longitude(BigDecimal.valueOf(126.85))
            .businessHours(businessHours)
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }
}
