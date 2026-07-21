package com.example.beyond_may_be.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CourseDetailServiceTest {

  @Test
  void returnsTemporaryCourseDetail() {
    CourseService service = new CourseService();

    CourseResponse response = service.getCourse("course_01J", "places,team,visits,route");

    assertEquals("course_01J", response.courseId());
    assertEquals("IN_PROGRESS", response.status());
    assertEquals(TravelSchedule.DAY_TRIP, response.durationType());
    assertEquals(3, response.summary().totalPlaceCount());
    assertTrue(response.places().getFirst().visitStatus().isVisited());
    assertEquals("OWNER", response.teamMembers().getFirst().role());
    assertEquals(OffsetDateTime.parse("2026-06-30T09:20:00+09:00"), response.confirmedAt());
    assertNull(response.completedAt());
  }
}
