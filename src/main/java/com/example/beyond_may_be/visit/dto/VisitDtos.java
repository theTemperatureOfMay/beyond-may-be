package com.example.beyond_may_be.visit.dto;

import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class VisitDtos {
  private VisitDtos() {}

  public record ConfirmRequest(
      @NotNull @Positive Long explorationId,
      @NotNull @Positive Long placeId,
      @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
      @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
      @NotNull @DecimalMin("0.0") @DecimalMax("50.0") BigDecimal accuracyMeters) {}

  public record ConfirmResponse(
      Long visitId,
      Long explorationId,
      Long participantId,
      Long placeId,
      Long coursePlaceId,
      boolean isCoursePlace,
      OffsetDateTime visitedAt,
      long distanceMeters,
      boolean teamFirstVisit,
      ExplorationDtos.CourseProgressResponse courseProgress,
      String explorationStatus) {}

  public record PhotoResponse(
      Long visitPhotoId,
      Long visitId,
      int displayOrder,
      String imageUrl,
      OffsetDateTime urlExpiresAt,
      OffsetDateTime uploadedAt) {}

  public record VisitConfirmedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      VisitConfirmedData data) {}

  public record VisitConfirmedData(
      Long visitId,
      Long participantId,
      String displayName,
      Long placeId,
      Long coursePlaceId,
      OffsetDateTime visitedAt,
      boolean teamFirstVisit,
      ExplorationDtos.CourseProgressResponse courseProgress,
      String explorationStatus) {}
}
