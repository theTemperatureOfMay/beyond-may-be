package com.example.beyond_may_be.course.converter;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CompleteResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseDetailPlaceResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseLocationResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CoursePlaceResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseShareDetailResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseSummaryResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.RouteResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.ShareResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.TeamMemberResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitStatusResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class CourseConverter {
  private CourseConverter() {}

  public static TripScheduleResponse toTripScheduleResponse(
      String scheduleId,
      TravelSchedule durationType,
      int minSelectablePlaceCount,
      int initialRecommendationLimit,
      int additionalRecommendationLimit,
      OffsetDateTime createdAt) {
    return new TripScheduleResponse(
        scheduleId,
        durationType,
        minSelectablePlaceCount,
        initialRecommendationLimit,
        additionalRecommendationLimit,
        createdAt);
  }

  public static AiGenerationResponse toAiGenerationResponse(int totalPlaceCount) {
    CoursePlaceResponse place = new CoursePlaceResponse(1, "place_001", "국립아시아문화전당", "09:30", 90);

    return new AiGenerationResponse(
        "course_temporary",
        "DRAFT",
        "하루치 광주",
        TravelSchedule.DAY_TRIP,
        totalPlaceCount,
        360,
        8200,
        List.of(place),
        new RouteResponse("encoded-polyline", "KAKAO"),
        OffsetDateTime.parse("2026-06-30T09:15:00+09:00"));
  }

  public static ConfirmResponse toConfirmResponse(String courseId) {
    return new ConfirmResponse(
        courseId,
        "CONFIRMED",
        "sess_temporary",
        new ShareResponse(
            "share_temporary",
            "https://gwangju.app/explore/" + courseId,
            OffsetDateTime.parse("2026-07-03T09:20:00+09:00"),
            5),
        OffsetDateTime.parse("2026-06-30T09:20:00+09:00"));
  }

  public static CourseResponse toCourseResponse(String courseId) {
    CourseDetailPlaceResponse place =
        new CourseDetailPlaceResponse(
            1,
            "place_001",
            "국립아시아문화전당",
            "문화",
            "광주광역시 동구 문화전당로 38",
            "https://cdn.example.com/places/place_001.jpg",
            new CourseLocationResponse(35.1469, 126.9199),
            "09:30",
            90,
            new VisitStatusResponse(
                true, OffsetDateTime.parse("2026-06-30T10:40:00+09:00"), "김감자감자"));
    TeamMemberResponse teamMember = new TeamMemberResponse("sess_temporary", "김감자감자", "OWNER", 1);

    return new CourseResponse(
        courseId,
        "하루치 광주",
        "IN_PROGRESS",
        TravelSchedule.DAY_TRIP,
        "sess_temporary",
        "MEMBER",
        new CourseSummaryResponse(3, 1, 4, 5, 360, 8200),
        new CourseShareDetailResponse(
            "share_temporary", false, OffsetDateTime.parse("2026-07-03T09:20:00+09:00")),
        List.of(place),
        List.of(teamMember),
        OffsetDateTime.parse("2026-06-30T09:15:00+09:00"),
        OffsetDateTime.parse("2026-06-30T09:20:00+09:00"),
        null);
  }

  public static CompleteResponse toCompleteResponse(Long scheduleId) {
    return new CompleteResponse(
        String.valueOf(scheduleId), "completed", LocalDateTime.parse("2026-06-30T12:00:00"), 4, 7);
  }

  public static VisitPlaceResponse toVisitPlaceResponse(String placeId) {
    return new VisitPlaceResponse(placeId, true, "1970-01-01T00:00:00", "temporary-user-id");
  }
}
