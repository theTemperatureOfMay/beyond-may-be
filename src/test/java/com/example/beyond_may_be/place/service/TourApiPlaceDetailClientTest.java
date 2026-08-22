package com.example.beyond_may_be.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class TourApiPlaceDetailClientTest {

  private MockRestServiceServer server;
  private TourApiPlaceDetailClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new TourApiPlaceDetailClient(builder.build(), "test-key");
  }

  @Test
  void fetchesDescriptionFromDetailCommon() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.containsString("/detailCommon2")))
        .andExpect(queryParam("serviceKey", "test-key"))
        .andExpect(queryParam("numOfRows", "1"))
        .andExpect(queryParam("pageNo", "1"))
        .andExpect(queryParam("MobileOS", "ETC"))
        .andExpect(queryParam("MobileApp", "BeyondMayBe"))
        .andExpect(queryParam("_type", "json"))
        .andExpect(queryParam("contentId", "126128"))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {"resultCode": "0000"},
                    "body": {"items": {"item": [{"overview": " 장소 설명 "}]}}
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchDescription(126128L)).isEqualTo("장소 설명");
    server.verify();
  }

  @Test
  void encodesDecodedServiceKeyOnceForDescription() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    MockRestServiceServer encodedServer = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceDetailClient encodedClient =
        new TourApiPlaceDetailClient(builder.build(), "abc+def/ghi=");
    encodedServer
        .expect(
            once(),
            requestTo(org.hamcrest.Matchers.containsString("serviceKey=abc%2Bdef%2Fghi%3D")))
        .andRespond(withRawStatus(500));

    assertThat(encodedClient.fetchDescription(126128L)).isNull();
    encodedServer.verify();
  }

  @ParameterizedTest
  @CsvSource({
    "12, usetime, 관광지 운영시간",
    "14, usetimeculture, 문화시설 운영시간",
    "15, playtime, 행사 시간",
    "28, usetimeleports, 레포츠 운영시간",
    "38, opentime, 쇼핑 운영시간",
    "39, opentimefood, 음식점 운영시간"
  })
  void mapsBusinessHoursByContentType(int contentTypeId, String field, String expected) {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.containsString("/detailIntro2")))
        .andExpect(queryParam("serviceKey", "test-key"))
        .andExpect(queryParam("numOfRows", "1"))
        .andExpect(queryParam("pageNo", "1"))
        .andExpect(queryParam("MobileOS", "ETC"))
        .andExpect(queryParam("MobileApp", "BeyondMayBe"))
        .andExpect(queryParam("_type", "json"))
        .andExpect(queryParam("contentId", "126128"))
        .andExpect(queryParam("contentTypeId", Integer.toString(contentTypeId)))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {"resultCode": "0000"},
                    "body": {"items": {"item": [{"%s": " %s "}]}}
                  }
                }
                """
                    .formatted(field, expected),
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchBusinessHours(126128L, contentTypeId)).isEqualTo(expected);
    server.verify();
  }

  @Test
  void encodesDecodedServiceKeyOnceForBusinessHours() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    MockRestServiceServer encodedServer = MockRestServiceServer.bindTo(builder).build();
    TourApiPlaceDetailClient encodedClient =
        new TourApiPlaceDetailClient(builder.build(), "abc+def/ghi=");
    encodedServer
        .expect(
            once(),
            requestTo(org.hamcrest.Matchers.containsString("serviceKey=abc%2Bdef%2Fghi%3D")))
        .andRespond(withRawStatus(500));

    assertThat(encodedClient.fetchBusinessHours(126128L, 12)).isNull();
    encodedServer.verify();
  }

  @Test
  void convertsTourApiErrorToNullWithoutRetrying() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(
            withSuccess(
                """
                {"response":{"header":{"resultCode":"99","resultMsg":"ERROR"}}}
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.fetchDescription(126128L)).isNull();
    server.verify();
  }

  @Test
  void convertsMalformedJsonToNull() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));

    assertThat(client.fetchDescription(126128L)).isNull();
    server.verify();
  }

  @Test
  void convertsTimeoutToNullWithoutRetrying() {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.any(String.class)))
        .andRespond(
            request -> {
              throw new ResourceAccessException("timeout");
            });

    assertThat(client.fetchBusinessHours(126128L, 12)).isNull();
    server.verify();
  }
}
