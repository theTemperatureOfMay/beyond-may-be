package com.example.beyond_may_be.route.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;
import com.example.beyond_may_be.route.client.KakaoRouteClient;
import com.example.beyond_may_be.route.dto.RouteDtos;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

  private static final BigDecimal START_LNG = new BigDecimal("126.900000");
  private static final BigDecimal START_LAT = new BigDecimal("35.100000");
  private static final BigDecimal END_LNG = new BigDecimal("126.910000");
  private static final BigDecimal END_LAT = new BigDecimal("35.110000");
  private static final BigDecimal BOARDING_LNG = new BigDecimal("126.905");
  private static final BigDecimal BOARDING_LAT = new BigDecimal("35.105");
  private static final BigDecimal ALIGHTING_LNG = new BigDecimal("126.908");
  private static final BigDecimal ALIGHTING_LAT = new BigDecimal("35.108");

  private final JsonMapper objectMapper = JsonMapper.builder().build();

  @Mock private KakaoRouteClient kakaoRouteClient;

  @Test
  void returnsWalkingRouteWhenOnlyWalkingJourneyExists() throws Exception {
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(walkingResponse("OK", "walk-1"));
    given(kakaoRouteClient.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(publicTransitResponse("NO_RESULTS"));

    RouteDtos.RouteResponse result = service().getRoute(START_LNG, START_LAT, END_LNG, END_LAT);

    assertThat(result.walking().get("id").asText()).isEqualTo("walk-1");
    assertThat(result.publicTransit()).isNull();
  }

  @Test
  void returnsWalkingAndPublicTransitRoutesWhenBothJourneyTypesExist() throws Exception {
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(walkingResponse("OK", "walk-1"));
    given(kakaoRouteClient.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(publicTransitResponse("OK", "bus-1"));

    RouteDtos.RouteResponse result = service().getRoute(START_LNG, START_LAT, END_LNG, END_LAT);

    assertThat(result.walking().get("id").asText()).isEqualTo("walk-1");
    assertThat(result.publicTransit().get("id").asText()).isEqualTo("bus-1");
  }

  @Test
  void returnsPublicTransitRouteWhenOnlyPublicTransitJourneyExists() throws Exception {
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(walkingResponse("ROUTE_RESULT_NOT_FOUND"));
    given(kakaoRouteClient.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(publicTransitResponse("OK", "subway-1"));

    RouteDtos.RouteResponse result = service().getRoute(START_LNG, START_LAT, END_LNG, END_LAT);

    assertThat(result.walking()).isNull();
    assertThat(result.publicTransit().get("id").asText()).isEqualTo("subway-1");
  }

  @Test
  void mergesWalkingSegmentsBeforeBoardingAndAfterAlighting() throws Exception {
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(walkingResponse("OK", "walk-1"));
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, BOARDING_LNG, BOARDING_LAT))
        .willReturn(walkingSegmentResponse(30, 3, "탑승지까지 이동"));
    given(kakaoRouteClient.fetchWalkingRoute(ALIGHTING_LNG, ALIGHTING_LAT, END_LNG, END_LAT))
        .willReturn(walkingSegmentResponse(40, 4, "목적지까지 이동"));
    given(kakaoRouteClient.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(publicTransitResponseWithStep());

    RouteDtos.RouteResponse result = service().getRoute(START_LNG, START_LAT, END_LNG, END_LAT);

    JsonNode publicTransit = result.publicTransit();
    assertThat(publicTransit.path("properties").path("totalDistance").asInt()).isEqualTo(170);
    assertThat(publicTransit.path("properties").path("totalTime").asInt()).isEqualTo(17);
    assertThat(publicTransit.path("steps")).hasSize(3);
    assertThat(publicTransit.path("steps").get(0).path("properties").path("type").asText())
        .isEqualTo("WALKING");
    assertThat(publicTransit.path("steps").get(1).path("properties").path("type").asText())
        .isEqualTo("BUS");
    assertThat(publicTransit.path("steps").get(2).path("properties").path("type").asText())
        .isEqualTo("WALKING");
  }

  @Test
  void returnsRouteUnavailableWhenWalkingSegmentLookupFails() throws Exception {
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(walkingResponse("OK", "walk-1"));
    given(kakaoRouteClient.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(publicTransitResponseWithStep());
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, BOARDING_LNG, BOARDING_LAT))
        .willThrow(new RestClientException("walking route unavailable"));

    assertThatThrownBy(() -> service().getRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .isInstanceOf(GeneralException.class)
        .satisfies(
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isSameAs(ErrorStatus.ROUTE_UNAVAILABLE));
  }

  @Test
  void throwsRouteNotFoundWhenNoJourneyTypeIsAvailable() throws Exception {
    given(kakaoRouteClient.fetchWalkingRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(walkingResponse("ROUTE_RESULT_NOT_FOUND"));
    given(kakaoRouteClient.fetchPublicTransitRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .willReturn(publicTransitResponse("NO_RESULTS"));

    assertThatThrownBy(() -> service().getRoute(START_LNG, START_LAT, END_LNG, END_LAT))
        .isInstanceOf(GeneralException.class)
        .satisfies(
            exception ->
                assertThat(((GeneralException) exception).getCode())
                    .isSameAs(ErrorStatus.ROUTE_NOT_FOUND));
  }

  private RouteService service() {
    return new RouteService(kakaoRouteClient);
  }

  private JsonNode walkingResponse(String status, String... routeIds) throws Exception {
    String route = routeIds.length == 0 ? "" : "\"id\": \"%s\"".formatted(routeIds[0]);
    return objectMapper.readTree(
        """
        {
          "status": "%s"%s
        }
        """
            .formatted(status, route.isEmpty() ? "" : ", \"route\": {%s}".formatted(route)));
  }

  private JsonNode publicTransitResponse(String status, String... routeIds) throws Exception {
    String routes = routeIds.length == 0 ? "" : "[{\"id\": \"%s\"}]".formatted(routeIds[0]);
    return objectMapper.readTree(
        """
        {"status": "%s"%s}
        """
            .formatted(status, routes.isEmpty() ? "" : ", \"routes\": " + routes));
  }

  private JsonNode publicTransitResponseWithStep() throws Exception {
    return objectMapper.readTree(
        """
        {
          "status": "OK",
          "routes": [{
            "properties": {"totalDistance": 100, "totalTime": 10},
            "steps": [{
              "properties": {"guidance": "버스 탑승", "type": "BUS", "distance": 100, "time": 10},
              "path": {"points": [[126.905, 35.105], [126.908, 35.108]]}
            }]
          }]
        }
        """);
  }

  private JsonNode walkingSegmentResponse(int distance, int time, String guidance)
      throws Exception {
    return objectMapper.readTree(
        """
        {
          "status": "OK",
          "route": {
            "properties": {"totalDistance": %d, "totalTime": %d},
            "legs": [{
              "steps": [{
                "properties": {"guidance": "%s", "distance": %d, "time": %d},
                "path": {"points": [[126.900, 35.100], [126.905, 35.105]]}
              }]
            }]
          }
        }
        """
            .formatted(distance, time, guidance, distance, time));
  }
}
