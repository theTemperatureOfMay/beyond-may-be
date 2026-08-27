package com.example.beyond_may_be.exploration.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class ExplorationDtos {
  private ExplorationDtos() {}

  public record LocationSharingUpdateRequest(
      @NotNull @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean enabled) {}

  public static final class StrictBooleanDeserializer extends StdDeserializer<Boolean> {
    public StrictBooleanDeserializer() {
      super(Boolean.class);
    }

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context)
        throws JacksonException {
      if (parser.hasToken(JsonToken.VALUE_TRUE) || parser.hasToken(JsonToken.VALUE_FALSE)) {
        return parser.getBooleanValue();
      }
      context.reportInputMismatch(Boolean.class, "boolean 값만 허용됩니다.");
      return null;
    }
  }

  public record LocationSharingResponse(
      Long explorationId,
      Long participantId,
      boolean locationSharingEnabled,
      OffsetDateTime updatedAt) {}

  public record LocationSharingChangedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      LocationSharingChangedData data) {}

  public record LocationSharingChangedData(Long participantId, boolean enabled) {}

  public record JoinResponse(
      Long explorationId,
      Long participantId,
      String role,
      String status,
      String displayName,
      boolean locationSharingEnabled,
      OffsetDateTime joinedAt,
      boolean alreadyJoined) {}

  public record ParticipantsResponse(
      Long explorationId, int participantCount, List<ParticipantResponse> participants) {}

  public record ParticipantResponse(
      Long participantId,
      String displayName,
      String role,
      String status,
      long visitedPlaceCount,
      boolean locationSharingEnabled,
      boolean isMe,
      OffsetDateTime joinedAt) {}

  public record StartResponse(
      Long explorationId,
      Long courseId,
      String status,
      Long participantId,
      OffsetDateTime startedAt) {}

  public record ExplorationStartedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      ExplorationStartedData data) {}

  public record ExplorationStartedData(
      String status, Long startedByParticipantId, OffsetDateTime startedAt) {}

  public record DetailResponse(
      Long explorationId,
      Long courseId,
      String status,
      Long startedByParticipantId,
      OffsetDateTime startedAt,
      OffsetDateTime completedAt,
      long participantCount,
      long teamVisitedPlaceCount,
      CourseProgressResponse courseProgress,
      CurrentParticipantResponse currentParticipant,
      PermissionsResponse permissions) {}

  public record CourseProgressResponse(
      long completedCoursePlaceCount, long totalCoursePlaceCount, int completionRate) {}

  public record CurrentParticipantResponse(
      Long participantId, String role, String status, boolean locationSharingEnabled) {}

  public record PermissionsResponse(boolean canStart, boolean canCompleteEarly) {}
}
