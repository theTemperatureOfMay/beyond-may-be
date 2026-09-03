package com.example.beyond_may_be.exploration.converter;

import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExplorationConverter {
  private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

  private ExplorationConverter() {}

  public static ExplorationDtos.LocationUpdatedEvent toLocationUpdatedEvent(
      UUID eventId,
      OffsetDateTime occurredAt,
      ExplorationParticipant participant,
      ExplorationDtos.LocationUpdateRequest request) {
    return new ExplorationDtos.LocationUpdatedEvent(
        eventId,
        "LOCATION_UPDATED",
        participant.getExplorationId(),
        occurredAt,
        new ExplorationDtos.LocationUpdatedData(
            participant.getId(),
            participant.getDisplayName(),
            request.latitude(),
            request.longitude(),
            request.accuracyMeters(),
            request.recordedAt()));
  }

  public static ExplorationDtos.ExplorationSummaryResponse toExplorationSummaryResponse(
      Exploration exploration,
      Course course,
      String representativeImageUrl,
      List<ExplorationParticipant> participants,
      long completedCoursePlaceCount,
      long totalCoursePlaceCount) {
    return new ExplorationDtos.ExplorationSummaryResponse(
        exploration.getId(),
        course.getId(),
        course.getTitle(),
        exploration.getStatus().name(),
        representativeImageUrl,
        participants.size(),
        participants.stream().map(ExplorationParticipant::getDisplayName).toList(),
        completedCoursePlaceCount,
        totalCoursePlaceCount,
        toOffsetDateTime(exploration.getStartedAt()),
        toOffsetDateTime(exploration.getCompletedAt()));
  }

  public static ExplorationDtos.ExplorationsResponse toExplorationsResponse(
      ExplorationStatus status, List<ExplorationDtos.ExplorationSummaryResponse> explorations) {
    return new ExplorationDtos.ExplorationsResponse(
        status.name(), explorations, explorations.size());
  }

  public static ExplorationDtos.JoinResponse toJoinResponse(
      ExplorationParticipant participant, boolean alreadyJoined) {
    return new ExplorationDtos.JoinResponse(
        participant.getExplorationId(),
        participant.getId(),
        participant.getRole().name(),
        participant.getStatus().name(),
        participant.getDisplayName(),
        participant.isLocationSharingEnabled(),
        toOffsetDateTime(participant.getJoinedAt()),
        alreadyJoined);
  }

  public static ExplorationDtos.ParticipantJoinedEvent toParticipantJoinedEvent(
      UUID eventId, ExplorationDtos.JoinResponse response, int participantCount) {
    return new ExplorationDtos.ParticipantJoinedEvent(
        eventId,
        "PARTICIPANT_JOINED",
        response.explorationId(),
        response.joinedAt(),
        new ExplorationDtos.ParticipantJoinedData(
            response.participantId(), response.displayName(), response.role(), participantCount));
  }

  public static ExplorationDtos.ParticipantsResponse toParticipantsResponse(
      Long explorationId,
      List<ExplorationParticipant> participants,
      Map<Long, Long> visitCounts,
      Long currentParticipantId) {
    List<ExplorationDtos.ParticipantResponse> responses =
        participants.stream()
            .map(
                participant ->
                    new ExplorationDtos.ParticipantResponse(
                        participant.getId(),
                        participant.getDisplayName(),
                        participant.getRole().name(),
                        participant.getStatus().name(),
                        visitCounts.getOrDefault(participant.getId(), 0L),
                        participant.isLocationSharingEnabled(),
                        participant.getId().equals(currentParticipantId),
                        toOffsetDateTime(participant.getJoinedAt())))
            .toList();
    return new ExplorationDtos.ParticipantsResponse(explorationId, responses.size(), responses);
  }

  public static ExplorationDtos.StartResponse toStartResponse(
      Exploration exploration, ExplorationParticipant participant, LocalDateTime startedAt) {
    return new ExplorationDtos.StartResponse(
        exploration.getId(),
        exploration.getCourseId(),
        ExplorationStatus.ONGOING.name(),
        participant.getId(),
        toOffsetDateTime(startedAt));
  }

  public static ExplorationDtos.ExplorationStartedEvent toExplorationStartedEvent(
      UUID eventId, ExplorationDtos.StartResponse response) {
    return new ExplorationDtos.ExplorationStartedEvent(
        eventId,
        "EXPLORATION_STARTED",
        response.explorationId(),
        response.startedAt(),
        new ExplorationDtos.ExplorationStartedData(
            response.status(), response.participantId(), response.startedAt()));
  }

  public static ExplorationDtos.CompleteResponse toCompleteResponse(
      Exploration exploration,
      String completionReason,
      long completedCoursePlaceCount,
      long totalCoursePlaceCount) {
    int completionRate =
        totalCoursePlaceCount == 0
            ? 0
            : (int) (completedCoursePlaceCount * 100 / totalCoursePlaceCount);
    return new ExplorationDtos.CompleteResponse(
        exploration.getId(),
        exploration.getCourseId(),
        exploration.getStatus().name(),
        completionReason,
        toOffsetDateTime(exploration.getCompletedAt()),
        new ExplorationDtos.CourseProgressResponse(
            completedCoursePlaceCount, totalCoursePlaceCount, completionRate));
  }

  public static ExplorationDtos.ExplorationCompletedEvent toExplorationCompletedEvent(
      UUID eventId, Long explorationId, LocalDateTime completedAt, String completionReason) {
    OffsetDateTime occurredAt = toOffsetDateTime(completedAt);
    return new ExplorationDtos.ExplorationCompletedEvent(
        eventId,
        "EXPLORATION_COMPLETED",
        explorationId,
        occurredAt,
        new ExplorationDtos.ExplorationCompletedData(
            ExplorationStatus.COMPLETED.name(), occurredAt, completionReason));
  }

  public static ExplorationDtos.LocationSharingResponse toLocationSharingResponse(
      ExplorationParticipant participant) {
    return new ExplorationDtos.LocationSharingResponse(
        participant.getExplorationId(),
        participant.getId(),
        participant.isLocationSharingEnabled(),
        toOffsetDateTime(participant.getUpdatedAt()));
  }

  public static ExplorationDtos.LocationSharingChangedEvent toLocationSharingChangedEvent(
      UUID eventId, ExplorationDtos.LocationSharingResponse response) {
    return new ExplorationDtos.LocationSharingChangedEvent(
        eventId,
        "LOCATION_SHARING_CHANGED",
        response.explorationId(),
        response.updatedAt(),
        new ExplorationDtos.LocationSharingChangedData(
            response.participantId(), response.locationSharingEnabled()));
  }

  public static ExplorationDtos.DetailResponse toDetailResponse(
      Exploration exploration,
      ExplorationParticipant currentParticipant,
      long participantCount,
      long teamVisitedPlaceCount,
      long completedCoursePlaceCount,
      long totalCoursePlaceCount,
      int completionRate,
      boolean canStart,
      boolean canCompleteEarly) {
    return new ExplorationDtos.DetailResponse(
        exploration.getId(),
        exploration.getCourseId(),
        exploration.getStatus().name(),
        exploration.getStartedByParticipantId(),
        toOffsetDateTime(exploration.getStartedAt()),
        toOffsetDateTime(exploration.getCompletedAt()),
        participantCount,
        teamVisitedPlaceCount,
        new ExplorationDtos.CourseProgressResponse(
            completedCoursePlaceCount, totalCoursePlaceCount, completionRate),
        new ExplorationDtos.CurrentParticipantResponse(
            currentParticipant.getId(),
            currentParticipant.getRole().name(),
            currentParticipant.getStatus().name(),
            currentParticipant.isLocationSharingEnabled()),
        new ExplorationDtos.PermissionsResponse(canStart, canCompleteEarly));
  }

  private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
    return value == null
        ? null
        : value
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(RESPONSE_ZONE)
            .toOffsetDateTime();
  }
}
