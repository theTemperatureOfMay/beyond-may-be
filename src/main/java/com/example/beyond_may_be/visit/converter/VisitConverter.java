package com.example.beyond_may_be.visit.converter;

import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.domain.VisitPhoto;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class VisitConverter {
  private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

  private VisitConverter() {}

  public static VisitDtos.VisitsResponse toVisitsResponse(
      Long explorationId, List<VisitDtos.VisitResponse> visits) {
    return new VisitDtos.VisitsResponse(explorationId, visits, visits.size());
  }

  public static VisitDtos.VisitResponse toVisitResponse(
      Visit visit,
      ExplorationParticipant participant,
      Place place,
      List<VisitDtos.VisitPhotoResponse> photos) {
    return new VisitDtos.VisitResponse(
        visit.getId(),
        new VisitDtos.ParticipantResponse(participant.getId(), participant.getDisplayName()),
        new VisitDtos.PlaceResponse(
            place.getId(),
            place.getName(),
            place.getCategory(),
            place.getTravelMbtiType(),
            place.getTags(),
            place.getAddress(),
            place.getThumbnailUrl()),
        visit.getCoursePlaceId(),
        visit.getCoursePlaceId() != null,
        toOffsetDateTime(visit.getVisitedAt()),
        photos);
  }

  public static VisitDtos.VisitPhotoResponse toVisitPhotoResponse(
      VisitPhoto photo, String imageUrl, Instant urlExpiresAt) {
    return new VisitDtos.VisitPhotoResponse(
        photo.getId(), photo.getDisplayOrder(), imageUrl, toOffsetDateTime(urlExpiresAt));
  }

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

  public static VisitDtos.PhotoResponse toPhotoResponse(
      VisitPhoto photo, String imageUrl, Instant urlExpiresAt) {
    return new VisitDtos.PhotoResponse(
        photo.getId(),
        photo.getVisitId(),
        photo.getDisplayOrder(),
        imageUrl,
        toOffsetDateTime(urlExpiresAt),
        toOffsetDateTime(photo.getCreatedAt()));
  }

  private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
    return value
        .atZone(ZoneId.systemDefault())
        .withZoneSameInstant(RESPONSE_ZONE)
        .toOffsetDateTime();
  }

  private static OffsetDateTime toOffsetDateTime(Instant value) {
    return value.atZone(RESPONSE_ZONE).toOffsetDateTime();
  }
}
