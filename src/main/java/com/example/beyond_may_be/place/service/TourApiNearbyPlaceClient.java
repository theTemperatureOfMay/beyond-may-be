package com.example.beyond_may_be.place.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TourApiNearbyPlaceClient {

  private static final Logger log = LoggerFactory.getLogger(TourApiNearbyPlaceClient.class);

  private final RestClient restClient;
  private final String serviceKey;

  public TourApiNearbyPlaceClient(
      @Qualifier("tourApiRestClient") RestClient restClient,
      @Value("${tour-api.service-key:}") String serviceKey) {
    this.restClient = restClient;
    this.serviceKey = serviceKey;
  }

  public List<NearbyItem> fetchNearbyPlaces(BigDecimal latitude, BigDecimal longitude) {
    if (serviceKey.isBlank()) {
      log.warn("TourAPI 인증키가 없어 저장된 주변 장소를 사용합니다.");
      return List.of();
    }
    try {
      TourApiResponse result =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/locationBasedList2")
                          .queryParam("serviceKey", "{serviceKey}")
                          .queryParam("numOfRows", 20)
                          .queryParam("pageNo", 1)
                          .queryParam("MobileOS", "ETC")
                          .queryParam("MobileApp", "BeyondMayBe")
                          .queryParam("_type", "json")
                          .queryParam("mapX", longitude.toPlainString())
                          .queryParam("mapY", latitude.toPlainString())
                          .queryParam("radius", 1_000)
                          .queryParam("arrange", "E")
                          .build(serviceKey))
              .retrieve()
              .body(TourApiResponse.class);
      if (result == null
          || result.response() == null
          || result.response().header() == null
          || !"0000".equals(result.response().header().resultCode())
          || result.response().body() == null
          || result.response().body().items() == null
          || result.response().body().items().item() == null) {
        log.warn("TourAPI 주변 장소 응답을 확인할 수 없어 저장된 장소를 사용합니다.");
        return List.of();
      }
      return List.copyOf(result.response().body().items().item());
    } catch (RuntimeException exception) {
      log.warn(
          "TourAPI 주변 장소 조회에 실패해 저장된 장소를 사용합니다. cause={}", exception.getClass().getSimpleName());
      return List.of();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TourApiResponse(Response response) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Response(Header header, Body body) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Header(String resultCode) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Body(Items items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Items(List<NearbyItem> item) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record NearbyItem(
      @JsonProperty("addr1") String address,
      @JsonProperty("addr2") String addressDetail,
      @JsonProperty("contentid") String contentId,
      @JsonProperty("contenttypeid") String contentTypeId,
      @JsonProperty("firstimage") String thumbnailUrl,
      @JsonProperty("mapx") String longitude,
      @JsonProperty("mapy") String latitude,
      @JsonProperty("areacode") String areaCode,
      String title,
      @JsonProperty("lclsSystm1") String largeCategoryCode,
      @JsonProperty("lclsSystm2") String middleCategoryCode,
      @JsonProperty("lclsSystm3") String smallCategoryCode) {}
}
