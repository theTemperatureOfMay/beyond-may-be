package com.example.beyond_may_be.visit.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class VisitDtos {
  private VisitDtos() {}

  public record ConfirmRequest(BigDecimal latitude, BigDecimal longitude) {}

  public record ConfirmResponse(
      Long visitId,
      Long placeId,
      boolean teamFirstVisit,
      boolean explorationCompleted,
      LocalDateTime visitedAt) {}
}
