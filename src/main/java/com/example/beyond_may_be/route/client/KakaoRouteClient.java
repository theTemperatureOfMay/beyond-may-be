package com.example.beyond_may_be.route.client;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoRouteClient {

  private static final String BASE_URL = "https://dapi.kakao.com";

  private final RestClient restClient;

  @Autowired
  public KakaoRouteClient(@Value("${kakao.map.rest-api-key}") String apiKey) {
    this(
        RestClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("Authorization", "KakaoAK " + apiKey)
            .build());
  }

  KakaoRouteClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public JsonNode fetchWalkingRoute(
      BigDecimal startLng, BigDecimal startLat, BigDecimal endLng, BigDecimal endLat) {
    return fetchRoute("/v2/routing/walk", startLng, startLat, endLng, endLat);
  }

  public JsonNode fetchPublicTransitRoute(
      BigDecimal startLng, BigDecimal startLat, BigDecimal endLng, BigDecimal endLat) {
    return fetchRoute("/v2/routing/publictraffic", startLng, startLat, endLng, endLat);
  }

  private JsonNode fetchRoute(
      String path, BigDecimal startLng, BigDecimal startLat, BigDecimal endLng, BigDecimal endLat) {
    return restClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path(path)
                    .queryParam("start_x", startLng)
                    .queryParam("start_y", startLat)
                    .queryParam("end_x", endLng)
                    .queryParam("end_y", endLat)
                    .build())
        .retrieve()
        .body(JsonNode.class);
  }
}
