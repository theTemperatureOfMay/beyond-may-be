package com.example.beyond_may_be.recommendation.dto;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class RecommendationDtos {
  private RecommendationDtos() {}

  public record CreateRequest(
      @NotNull TravelSchedule travelSchedule,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate) {}

  public record RecommendationResponse(
      Long recommendationId,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      int minimumSelectionCount,
      BatchResponse batch) {}

  public record BatchResponse(int batchNumber, List<PlaceResponse> places) {}

  public record PlaceResponse(
      Long placeId,
      String name,
      String category,
      List<String> tags,
      String summary,
      String thumbnailUrl) {}
}
