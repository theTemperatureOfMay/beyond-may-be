package com.example.beyond_may_be.exploration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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

  public record IdResponse(Long explorationId) {}

  public record ActiveExplorationResponse(Long activeExplorationId) {}

  public record LeaveResponse(
      Long explorationId,
      Long participantId,
      String status,
      OffsetDateTime leftAt,
      @Schema(nullable = true) Long ownerParticipantId) {}

  public record ParticipantLeftEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      ParticipantLeftData data) {}

  public record ParticipantLeftData(
      Long participantId, int participantCount, @Schema(nullable = true) Long ownerParticipantId) {}

  public record LocationUpdateRequest(
      @NotNull
          @DecimalMin("-90.0")
          @DecimalMax("90.0")
          @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
          BigDecimal latitude,
      @NotNull
          @DecimalMin("-180.0")
          @DecimalMax("180.0")
          @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
          BigDecimal longitude,
      @NotNull
          @DecimalMin("0.0")
          @DecimalMax("50.0")
          @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
          BigDecimal accuracyMeters,
      @NotNull @JsonDeserialize(using = StrictOffsetDateTimeDeserializer.class)
          OffsetDateTime recordedAt) {}

  public static final class StrictBigDecimalDeserializer extends StdDeserializer<BigDecimal> {
    public StrictBigDecimalDeserializer() {
      super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context)
        throws JacksonException {
      if (parser.hasToken(JsonToken.VALUE_NUMBER_INT)
          || parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
        return parser.getDecimalValue();
      }
      context.reportInputMismatch(BigDecimal.class, "숫자 값만 허용됩니다.");
      return null;
    }
  }

  public static final class StrictOffsetDateTimeDeserializer
      extends StdDeserializer<OffsetDateTime> {
    public StrictOffsetDateTimeDeserializer() {
      super(OffsetDateTime.class);
    }

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context)
        throws JacksonException {
      if (parser.hasToken(JsonToken.VALUE_STRING)) {
        try {
          return OffsetDateTime.parse(parser.getValueAsString());
        } catch (DateTimeParseException exception) {
          context.reportInputMismatch(OffsetDateTime.class, "OffsetDateTime 값이어야 합니다.");
        }
      }
      context.reportInputMismatch(OffsetDateTime.class, "문자열 OffsetDateTime 값만 허용됩니다.");
      return null;
    }
  }

  public record LocationUpdatedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      LocationUpdatedData data) {}

  public record LocationUpdatedData(
      Long participantId,
      String displayName,
      BigDecimal latitude,
      BigDecimal longitude,
      BigDecimal accuracyMeters,
      OffsetDateTime recordedAt) {}

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

  public record ParticipantJoinedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      ParticipantJoinedData data) {}

  public record ParticipantJoinedData(
      Long participantId, String displayName, String role, int participantCount) {}

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

  public record CompleteResponse(
      Long explorationId,
      Long courseId,
      String status,
      String completionReason,
      OffsetDateTime completedAt,
      CourseProgressResponse courseProgress) {}

  public record ExplorationStartedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      ExplorationStartedData data) {}

  public record ExplorationStartedData(
      String status, Long startedByParticipantId, OffsetDateTime startedAt) {}

  public record ExplorationCompletedEvent(
      UUID eventId,
      String eventType,
      Long explorationId,
      OffsetDateTime occurredAt,
      ExplorationCompletedData data) {}

  public record ExplorationCompletedData(
      String status, OffsetDateTime completedAt, String completionReason) {}

  public record ExplorationsResponse(
      String status, List<ExplorationSummaryResponse> explorations, int totalCount) {}

  public record ExplorationSummaryResponse(
      Long explorationId,
      Long courseId,
      String courseTitle,
      String status,
      @Schema(nullable = true) String representativeImageUrl,
      int participantCount,
      List<String> participantDisplayNames,
      long completedCoursePlaceCount,
      long totalCoursePlaceCount,
      OffsetDateTime startedAt,
      @Schema(nullable = true) OffsetDateTime completedAt) {}

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
