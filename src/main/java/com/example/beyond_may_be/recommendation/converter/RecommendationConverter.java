package com.example.beyond_may_be.recommendation.converter;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.recommendation.dto.RecommendationDtos;
import java.util.List;

public final class RecommendationConverter {
  private RecommendationConverter() {}

  public static RecommendationDtos.RecommendationResponse toRecommendationResponse(
      RecommendationSet recommendationSet,
      int minimumSelectionCount,
      int batchNumber,
      List<Place> places) {
    return new RecommendationDtos.RecommendationResponse(
        recommendationSet.getId(),
        recommendationSet.getTravelSchedule(),
        recommendationSet.getStartDate(),
        recommendationSet.getEndDate(),
        minimumSelectionCount,
        new RecommendationDtos.BatchResponse(
            batchNumber, places.stream().map(RecommendationConverter::toPlaceResponse).toList()));
  }

  private static RecommendationDtos.PlaceResponse toPlaceResponse(Place place) {
    return new RecommendationDtos.PlaceResponse(
        place.getId(),
        place.getName(),
        place.getCategory(),
        List.copyOf(place.getTags()),
        place.getDescription(),
        place.getThumbnailUrl());
  }
}
