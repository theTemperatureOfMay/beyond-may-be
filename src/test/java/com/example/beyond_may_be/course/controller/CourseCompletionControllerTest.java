package com.example.beyond_may_be.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CompleteResponse;
import com.example.beyond_may_be.course.service.CourseService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CourseCompletionControllerTest {

  @Test
  void delegatesCourseCompletionToService() {
    CourseService courseService = mock(CourseService.class);
    ScheduleController controller = new ScheduleController(courseService);
    CompleteResponse expectedResponse =
        new CompleteResponse(
            "schedule_01J", "completed", LocalDateTime.parse("2026-07-22T10:30:00"), 4, 7);
    when(courseService.completeCourse(1L)).thenReturn(expectedResponse);

    ApiResponse<CompleteResponse> response = controller.completeCourse(1L);

    assertEquals(expectedResponse, response.getData());
    verify(courseService).completeCourse(1L);
  }
}
