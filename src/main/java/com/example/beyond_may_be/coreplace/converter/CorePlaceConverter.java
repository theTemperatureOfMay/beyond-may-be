package com.example.beyond_may_be.coreplace.converter;

import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.LocationResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.NearbyPlaceResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.NearbyPlacesResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.PageInfoResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationItemResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchItemResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchResponse;
import java.util.List;

public final class CorePlaceConverter {
  private CorePlaceConverter() {}

  public static RecommendationsResponse toRecommendationsResponse(
      String scheduleId, int recommendationBatchSize) {
    RecommendationItemResponse item =
        new RecommendationItemResponse(
            "place_001",
            "국립아시아문화전당",
            "문화",
            List.of("5.18", "전시", "도보"),
            "광주의 문화와 역사를 함께 볼 수 있는 복합문화공간",
            "https://cdn.example.com/places/place_001.jpg",
            "광주광역시 동구 문화전당로 38",
            new LocationResponse(35.1469, 126.9199),
            "사색러 성향과 문화/역사 관심도에 잘 맞습니다.");

    return new RecommendationsResponse(
        scheduleId,
        "SASEAK",
        "DAY_TRIP",
        3,
        2,
        recommendationBatchSize,
        List.of(item),
        new PageInfoResponse("eyJvZmZzZXQiOjEw", true));
  }

  public static SearchResponse toSearchResponse() {
    SearchItemResponse item =
        new SearchItemResponse(
            "place_010",
            "양림동 펭귄마을",
            "HISTORY",
            List.of("근대골목", "포토스팟"),
            "양림동 골목의 분위기를 느낄 수 있는 장소",
            "https://cdn.example.com/places/place_010.jpg",
            "광주광역시 남구 천변좌로446번길 7",
            new LocationResponse(35.1401, 126.9112),
            850,
            false);
    return new SearchResponse(List.of(item));
  }

  public static NearbyPlacesResponse toNearbyPlacesResponse() {
    NearbyPlaceResponse place =
        new NearbyPlaceResponse(
            "place_001", "국립아시아문화전당", "문화", 320, "https://cdn.example.com/places/place_001.jpg");
    return new NearbyPlacesResponse(List.of(place));
  }
}
