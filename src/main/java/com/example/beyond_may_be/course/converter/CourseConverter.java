package com.example.beyond_may_be.course.converter;

import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.place.domain.Place;
import java.util.List;
import java.util.Map;

public final class CourseConverter {
  private CourseConverter() {}

  public static CourseDtos.ConfirmResponse toConfirmResponse(
      Course course, Exploration exploration) {
    return new CourseDtos.ConfirmResponse(
        course.getId(),
        exploration.getId(),
        course.getStatus().name(),
        course.getConfirmedAt(),
        course.getShareExpiresAt());
  }

  public static CourseDtos.CourseDetailResponse toCourseDetailResponse(
      Course course, List<CoursePlace> coursePlaces, Map<Long, Place> placesById) {
    // course_places.place_id는 places.place_id에 대한 DB 레벨 FK 제약이 없어
    // 참조가 끊어진 행이 있을 수 있다. 그런 행은 조용히 건너뛴다.
    List<CourseDtos.CoursePlaceSummary> places =
        coursePlaces.stream()
            .filter(coursePlace -> placesById.containsKey(coursePlace.getPlaceId()))
            .map(coursePlace -> toCoursePlaceSummary(coursePlace, placesById))
            .toList();
    return new CourseDtos.CourseDetailResponse(
        course.getId(),
        course.getTitle(),
        course.getStatus().name(),
        course.getTravelSchedule().name(),
        course.getStartDate(),
        course.getEndDate(),
        course.getStartTime(),
        places);
  }

  private static CourseDtos.CoursePlaceSummary toCoursePlaceSummary(
      CoursePlace coursePlace, Map<Long, Place> placesById) {
    Place place = placesById.get(coursePlace.getPlaceId());
    return new CourseDtos.CoursePlaceSummary(
        place.getId(),
        place.getName(),
        place.getCategory(),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        coursePlace.getDayNumber(),
        coursePlace.getVisitOrder(),
        coursePlace.getEstimatedStayMinutes(),
        coursePlace.getTravelModeFromPrevious());
  }
}
