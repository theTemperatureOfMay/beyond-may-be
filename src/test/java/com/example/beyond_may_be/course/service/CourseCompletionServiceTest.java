package com.example.beyond_may_be.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.course.dto.CourseDtos.CompleteResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CourseCompletionServiceTest {

  @Test
  void returnsTemporaryCourseCompletionResponse() {
    CourseService service = new CourseService();

    CompleteResponse response = service.completeCourse(1L);

    assertEquals("1", response.scheduleId());
    assertEquals("completed", response.status());
    assertEquals(LocalDateTime.parse("2026-06-30T12:00:00"), response.completedAt());
    assertEquals(4, response.visitedPlaceCount());
    assertEquals(7, response.totalPlaceCount());
  }
}
