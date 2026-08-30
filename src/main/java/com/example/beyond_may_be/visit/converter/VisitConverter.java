package com.example.beyond_may_be.visit.converter;

import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public final class VisitConverter {
  private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

  private VisitConverter() {}

  public static VisitDtos.ConfirmResponse toConfirmResponse(
      Visit visit,
      Long explorationId,
      long distanceMeters,
      boolean teamFirstVisit,
      long completedCoursePlaceCount,
      long totalCoursePlaceCount,
      String explorationStatus) {
    int completionRate =
        totalCoursePlaceCount == 0
            ? 0
            : (int) (completedCoursePlaceCount * 100 / totalCoursePlaceCount);
    return new VisitDtos.ConfirmResponse(
        visit.getId(),
        explorationId,
        visit.getParticipantId(),
        visit.getPlaceId(),
        visit.getCoursePlaceId(),
        visit.getCoursePlaceId() != null,
        toOffsetDateTime(visit.getVisitedAt()),
        distanceMeters,
        teamFirstVisit,
        new ExplorationDtos.CourseProgressResponse(
            completedCoursePlaceCount, totalCoursePlaceCount, completionRate),
        explorationStatus);
  }

  public static VisitDtos.VisitConfirmedEvent toVisitConfirmedEvent(
      UUID eventId, VisitDtos.ConfirmResponse response, String displayName) {
    return new VisitDtos.VisitConfirmedEvent(
        eventId,
        "VISIT_CONFIRMED",
        response.explorationId(),
        response.visitedAt(),
        new VisitDtos.VisitConfirmedData(
            response.visitId(),
            response.participantId(),
            displayName,
            response.placeId(),
            response.coursePlaceId(),
            response.visitedAt(),
            response.teamFirstVisit(),
            response.courseProgress(),
            response.explorationStatus()));
  }

  public static ExplorationDtos.ExplorationCompletedEvent toExplorationCompletedEvent(
      UUID eventId, Long explorationId, LocalDateTime completedAt) {
    OffsetDateTime occurredAt = toOffsetDateTime(completedAt);
    return new ExplorationDtos.ExplorationCompletedEvent(
        eventId,
        "EXPLORATION_COMPLETED",
        explorationId,
        occurredAt,
        new ExplorationDtos.ExplorationCompletedData(
            "COMPLETED", occurredAt, "ALL_COURSE_PLACES_VISITED"));
  }

  private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
    return value
        .atZone(ZoneId.systemDefault())
        .withZoneSameInstant(RESPONSE_ZONE)
        .toOffsetDateTime();
  }
}
