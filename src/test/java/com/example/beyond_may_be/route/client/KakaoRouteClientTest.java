package com.example.beyond_may_be.route.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class KakaoRouteClientTest {

  private MockRestServiceServer server;
  private KakaoRouteClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client =
        new KakaoRouteClient(builder.defaultHeader("Authorization", "KakaoAK test-key").build());
  }

  @Test
  void requestsWalkingRoute() {
    expectRouteRequest("/v2/routing/walk");

    JsonNode response = client.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT);

    assertThat(response.path("status").asText()).isEqualTo("OK");
    server.verify();
  }

  @Test
  void requestsPublicTransitRoute() {
    expectRouteRequest("/v2/routing/publictraffic");

    JsonNode response = client.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT);

    assertThat(response.path("status").asText()).isEqualTo("OK");
    server.verify();
  }

  private static final BigDecimal START_LNG = new BigDecimal("126.900000");
  private static final BigDecimal START_LAT = new BigDecimal("35.100000");
  private static final BigDecimal END_LNG = new BigDecimal("126.910000");
  private static final BigDecimal END_LAT = new BigDecimal("35.110000");

  private void expectRouteRequest(String path) {
    server
        .expect(once(), requestTo(org.hamcrest.Matchers.containsString(path)))
        .andExpect(header("Authorization", "KakaoAK test-key"))
        .andExpect(queryParam("start_x", "126.900000"))
        .andExpect(queryParam("start_y", "35.100000"))
        .andExpect(queryParam("end_x", "126.910000"))
        .andExpect(queryParam("end_y", "35.110000"))
        .andRespond(
            withSuccess(
                """
                {"status": "OK"}
                """,
                MediaType.APPLICATION_JSON));
  }
}
