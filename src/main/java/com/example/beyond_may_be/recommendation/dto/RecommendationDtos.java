package com.example.beyond_may_be.recommendation.dto;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class RecommendationDtos {
  private RecommendationDtos() {}

  public record CreateRequest(
      @NotNull TravelSchedule travelSchedule,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate) {}

  public record ReactionRequest(
      @NotNull List<@NotNull Long> likedPlaceIds, @NotNull List<@NotNull Long> dislikedPlaceIds) {}

  public record RecommendationResponse(
      Long recommendationId,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      int minimumSelectionCount,
      BatchResponse batch) {}

  public record CurrentRecommendationResponse(
      Long recommendationId,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      int batchSize,
      int minimumSelectionCount,
      int selectedPlaceCount,
      boolean selectionReady,
      List<BatchStateResponse> batches) {}

  public record BatchStateResponse(
      int batchNumber,
      List<PlaceResponse> places,
      List<Long> likedPlaceIds,
      List<Long> dislikedPlaceIds,
      boolean completed) {}

  public record BatchResponse(int batchNumber, List<PlaceResponse> places) {}

  public record ReactionResponse(
      Long recommendationId,
      int batchNumber,
      int selectedPlaceCount,
      int minimumSelectionCount,
      boolean selectionReady,
      boolean hasNextBatch,
      BatchResponse nextBatch) {}

  public record PlaceResponse(
      Long placeId,
      String name,
      String category,
      List<String> tags,
      @Schema(nullable = true) String summary,
      @Schema(nullable = true) String thumbnailUrl) {}
}
