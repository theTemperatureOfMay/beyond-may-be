package com.example.beyond_may_be.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CourseServiceTest {

  @Test
  void returnsTemporaryTripScheduleResponse() {
    CourseService service = new CourseService();
    TripScheduleRequest request =
        new TripScheduleRequest(
            TravelSchedule.DAY_TRIP, LocalDate.parse("2026-07-12"), LocalDate.parse("2026-07-12"));

    TripScheduleResponse response = service.createTripSchedule(request);

    assertEquals("schedule_temporary", response.scheduleId());
    assertEquals(TravelSchedule.DAY_TRIP, response.durationType());
    assertEquals(3, response.minSelectablePlaceCount());
    assertEquals(10, response.initialRecommendationLimit());
    assertEquals(10, response.additionalRecommendationLimit());
    assertEquals(OffsetDateTime.parse("2026-06-30T09:00:00+09:00"), response.createdAt());
  }
}
