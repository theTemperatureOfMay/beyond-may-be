package com.example.beyond_may_be.course.dto;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class CourseDtos {
  private CourseDtos() {}

  public record TripScheduleRequest(
      TravelSchedule durationType, LocalDate startDate, LocalDate endDate) {}

  public record TripScheduleResponse(
      String scheduleId,
      TravelSchedule durationType,
      int minSelectablePlaceCount,
      int initialRecommendationLimit,
      int additionalRecommendationLimit,
      OffsetDateTime createdAt) {}

  public record AiGenerationRequest(
      String scheduleId,
      List<String> selectedPlaceIds,
      String startTime,
      List<String> transportModes) {}

  public record AiGenerationResponse(
      String courseId,
      String status,
      String title,
      TravelSchedule durationType,
      int totalPlaceCount,
      int estimatedDurationMinutes,
      int estimatedDistanceMeters,
      List<CoursePlaceResponse> places,
      RouteResponse route,
      OffsetDateTime createdAt) {}

  public record CoursePlaceResponse(
      int order,
      String placeId,
      String name,
      String estimatedArrivalTime,
      int estimatedStayMinutes) {}

  public record RouteResponse(String polyline, String mapProvider) {}

  public record ConfirmRequest(boolean confirm) {}

  public record ConfirmResponse(
      String courseId,
      String status,
      String ownerSessionId,
      ShareResponse share,
      OffsetDateTime confirmedAt) {}

  public record ShareResponse(
      String shareId, String shareUrl, OffsetDateTime expiresAt, int maxMemberCount) {}

  public record CourseResponse(
      String courseId,
      String title,
      String status,
      TravelSchedule durationType,
      String ownerSessionId,
      String myRole,
      CourseSummaryResponse summary,
      CourseShareDetailResponse share,
      List<CourseDetailPlaceResponse> places,
      List<TeamMemberResponse> teamMembers,
      OffsetDateTime createdAt,
      OffsetDateTime confirmedAt,
      OffsetDateTime completedAt) {}

  public record CourseSummaryResponse(
      int totalPlaceCount,
      int visitedPlaceCount,
      int teamMemberCount,
      int maxMemberCount,
      int estimatedDurationMinutes,
      int estimatedDistanceMeters) {}

  public record CourseShareDetailResponse(
      String shareId, boolean isExpiredForNewJoin, OffsetDateTime expiresAt) {}

  public record CourseDetailPlaceResponse(
      int order,
      String placeId,
      String name,
      String category,
      String address,
      String thumbnailUrl,
      CourseLocationResponse location,
      String estimatedArrivalTime,
      int estimatedStayMinutes,
      VisitStatusResponse visitStatus) {}

  public record CourseLocationResponse(double lat, double lng) {}

  public record VisitStatusResponse(
      boolean isVisited, OffsetDateTime visitedAt, String verifiedByNickname) {}

  public record TeamMemberResponse(
      String sessionId, String nickname, String role, int visitedPlaceCount) {}

  public record CompleteResponse(
      String scheduleId,
      String status,
      LocalDateTime completedAt,
      int visitedPlaceCount,
      int totalPlaceCount) {}
}
