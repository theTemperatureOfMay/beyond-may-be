package com.example.beyond_may_be.visit.converter;

import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.dto.VisitDtos;

public final class VisitConverter {
  private VisitConverter() {}

  public static VisitDtos.ConfirmResponse toConfirmResponse(
      Visit visit, boolean teamFirstVisit, boolean explorationCompleted) {
    return new VisitDtos.ConfirmResponse(
        visit.getId(),
        visit.getPlaceId(),
        teamFirstVisit,
        explorationCompleted,
        visit.getVisitedAt());
  }
}
