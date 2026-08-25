package com.example.beyond_may_be.place.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TourApiSyncClient {

  private static final Logger log = LoggerFactory.getLogger(TourApiSyncClient.class);

  private final RestClient restClient;
  private final String serviceKey;

  public TourApiSyncClient(
      @Qualifier("tourApiRestClient") RestClient restClient,
      @Value("${tour-api.service-key:}") String serviceKey) {
    this.restClient = restClient;
    this.serviceKey = serviceKey;
  }

  public List<SyncItem> fetchChangedPlaces() {
    // 3-1. 성향별 후보가 부족할 때 areaBasedSyncList2로 광주 변경분 한 페이지를 한 번 조회한다.
    // 키가 없거나 조회·응답 검증이 실패하면 빈 목록을 반환해 현재 DB 후보로 계속 진행한다.
    if (serviceKey.isBlank()) {
      log.warn("TourAPI 인증키가 없어 변경분 조회를 건너뛰고 현재 DB 장소만 사용합니다.");
      return List.of();
    }
    try {
      TourApiResponse result =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/areaBasedSyncList2")
                          .queryParam("serviceKey", "{serviceKey}")
                          .queryParam("numOfRows", 200)
                          .queryParam("pageNo", 1)
                          .queryParam("MobileOS", "ETC")
                          .queryParam("MobileApp", "BeyondMayBe")
                          .queryParam("_type", "json")
                          .queryParam("showflag", 1)
                          .queryParam("modifiedtime", "20260821")
                          .queryParam("areaCode", 5)
                          .queryParam("lDongRegnCd", 12)
                          .build(serviceKey))
              .retrieve()
              .body(TourApiResponse.class);
      if (result == null || result.response() == null || result.response().header() == null) {
        log.warn("TourAPI 변경분 조회 응답을 확인할 수 없어 현재 DB 장소만 사용합니다.");
        return List.of();
      }
      if (!"0000".equals(result.response().header().resultCode())) {
        log.warn(
            "TourAPI 변경분 조회 오류 응답으로 현재 DB 장소만 사용합니다. resultCode={}",
            result.response().header().resultCode());
        return List.of();
      }
      if (result.response().body() == null
          || result.response().body().items() == null
          || result.response().body().items().item() == null) {
        return List.of();
      }
      return List.copyOf(result.response().body().items().item());
    } catch (RuntimeException exception) {
      log.warn(
          "TourAPI 변경분 조회에 실패해 현재 DB 장소만 사용합니다. cause={}", exception.getClass().getSimpleName());
      return List.of();
    }
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
  record Items(List<SyncItem> item) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SyncItem(
      @JsonProperty("addr1") String address,
      @JsonProperty("addr2") String addressDetail,
      @JsonProperty("contentid") String contentId,
      @JsonProperty("contenttypeid") String contentTypeId,
      @JsonProperty("firstimage") String thumbnailUrl,
      @JsonProperty("mapx") String longitude,
      @JsonProperty("mapy") String latitude,
      @JsonProperty("showflag") String showFlag,
      String title,
      @JsonProperty("lclsSystm1") String largeCategoryCode,
      @JsonProperty("lclsSystm2") String middleCategoryCode,
      @JsonProperty("lclsSystm3") String smallCategoryCode) {}
}
