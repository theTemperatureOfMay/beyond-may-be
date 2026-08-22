package com.example.beyond_may_be.place.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TourApiPlaceDetailClient {

  private static final Logger log = LoggerFactory.getLogger(TourApiPlaceDetailClient.class);

  private final RestClient restClient;
  private final String serviceKey;

  public TourApiPlaceDetailClient(
      @Qualifier("tourApiRestClient") RestClient restClient,
      @Value("${tour-api.service-key:}") String serviceKey) {
    this.restClient = restClient;
    this.serviceKey = serviceKey;
  }

  public String fetchDescription(long contentId) {
    // 7-3. detailCommon2의 overview를 장소 설명으로 사용한다.
    if (serviceKey.isBlank()) {
      return null;
    }
    try {
      return nonBlank(
          firstItem(
                  restClient
                      .get()
                      .uri(
                          uriBuilder ->
                              uriBuilder
                                  .path("/detailCommon2")
                                  .queryParam("serviceKey", "{serviceKey}")
                                  .queryParam("numOfRows", 1)
                                  .queryParam("pageNo", 1)
                                  .queryParam("MobileOS", "ETC")
                                  .queryParam("MobileApp", "BeyondMayBe")
                                  .queryParam("_type", "json")
                                  .queryParam("contentId", contentId)
                                  .build(serviceKey))
                      .retrieve()
                      .body(TourApiResponse.class))
              .overview());
    } catch (RuntimeException ignored) {
      log.warn("TourAPI 장소 설명 조회에 실패했습니다.");
      return null;
    }
  }

  public String fetchBusinessHours(long contentId, int contentTypeId) {
    // 7-4. detailIntro2는 콘텐츠 유형마다 다른 운영시간 필드를 골라 사용한다.
    if (serviceKey.isBlank()) {
      return null;
    }
    try {
      DetailItem item =
          firstItem(
              restClient
                  .get()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .path("/detailIntro2")
                              .queryParam("serviceKey", "{serviceKey}")
                              .queryParam("numOfRows", 1)
                              .queryParam("pageNo", 1)
                              .queryParam("MobileOS", "ETC")
                              .queryParam("MobileApp", "BeyondMayBe")
                              .queryParam("_type", "json")
                              .queryParam("contentId", contentId)
                              .queryParam("contentTypeId", contentTypeId)
                              .build(serviceKey))
                  .retrieve()
                  .body(TourApiResponse.class));
      return nonBlank(
          switch (contentTypeId) {
            case 12 -> item.usetime();
            case 14 -> item.usetimeculture();
            case 15 -> item.playtime();
            case 28 -> item.usetimeleports();
            case 38 -> item.opentime();
            case 39 -> item.opentimefood();
            default -> null;
          });
    } catch (RuntimeException ignored) {
      log.warn("TourAPI 장소 운영시간 조회에 실패했습니다.");
      return null;
    }
  }

  private DetailItem firstItem(TourApiResponse result) {
    if (result != null
        && result.response() != null
        && result.response().header() != null
        && !"0000".equals(result.response().header().resultCode())) {
      log.warn("TourAPI 장소 상세정보 오류 응답을 받았습니다.");
    }
    if (result == null
        || result.response() == null
        || result.response().header() == null
        || !"0000".equals(result.response().header().resultCode())
        || result.response().body() == null
        || result.response().body().items() == null
        || result.response().body().items().item() == null
        || result.response().body().items().item().isEmpty()) {
      return new DetailItem(null, null, null, null, null, null, null);
    }
    return result.response().body().items().item().getFirst();
  }

  private String nonBlank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TourApiResponse(Response response) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Response(Header header, Body body) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Header(String resultCode) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Body(Items items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Items(List<DetailItem> item) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DetailItem(
      String overview,
      String usetime,
      String usetimeculture,
      String playtime,
      String usetimeleports,
      String opentime,
      String opentimefood) {}
}
