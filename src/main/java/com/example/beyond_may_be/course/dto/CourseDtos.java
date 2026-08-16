package com.example.beyond_may_be.course.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class CourseDtos {
  private CourseDtos() {}

  public record ConfirmResponse(
      Long courseId,
      Long explorationId,
      String status,
      LocalDateTime confirmedAt,
      LocalDateTime shareExpiresAt) {}

  public record CoursePlaceSummary(
      Long placeId,
      String name,
      String category,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      int dayNumber,
      int visitOrder,
      int estimatedStayMinutes,
      String travelModeFromPrevious) {}

  public record CourseDetailResponse(
      Long courseId,
      String title,
      String status,
      String travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      List<CoursePlaceSummary> places) {}
}
