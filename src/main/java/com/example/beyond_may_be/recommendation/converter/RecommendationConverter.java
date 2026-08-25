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
        toBatchResponse(batchNumber, places));
  }

  public static RecommendationDtos.BatchResponse toBatchResponse(
      int batchNumber, List<Place> places) {
    return new RecommendationDtos.BatchResponse(
        batchNumber, places.stream().map(RecommendationConverter::toPlaceResponse).toList());
  }

  public static RecommendationDtos.CurrentRecommendationResponse toCurrentRecommendationResponse(
      RecommendationSet recommendationSet,
      int batchSize,
      int minimumSelectionCount,
      List<RecommendationDtos.BatchStateResponse> batches) {
    int selectedPlaceCount = recommendationSet.getLikedPlaceIds().size();
    return new RecommendationDtos.CurrentRecommendationResponse(
        recommendationSet.getId(),
        recommendationSet.getTravelSchedule(),
        recommendationSet.getStartDate(),
        recommendationSet.getEndDate(),
        batchSize,
        minimumSelectionCount,
        selectedPlaceCount,
        selectedPlaceCount >= minimumSelectionCount,
        batches);
  }

  public static RecommendationDtos.BatchStateResponse toBatchStateResponse(
      int batchNumber,
      List<Place> places,
      List<Long> likedPlaceIds,
      List<Long> dislikedPlaceIds,
      boolean completed) {
    return new RecommendationDtos.BatchStateResponse(
        batchNumber,
        places.stream().map(RecommendationConverter::toPlaceResponse).toList(),
        likedPlaceIds,
        dislikedPlaceIds,
        completed);
  }

  public static RecommendationDtos.ReactionResponse toReactionResponse(
      Long recommendationId,
      int batchNumber,
      int selectedPlaceCount,
      int minimumSelectionCount,
      boolean selectionReady,
      boolean hasNextBatch,
      RecommendationDtos.BatchResponse nextBatch) {
    return new RecommendationDtos.ReactionResponse(
        recommendationId,
        batchNumber,
        selectedPlaceCount,
        minimumSelectionCount,
        selectionReady,
        hasNextBatch,
        nextBatch);
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
