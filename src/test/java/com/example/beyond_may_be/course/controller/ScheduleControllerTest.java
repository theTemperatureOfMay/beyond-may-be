package com.example.beyond_may_be.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceResponse;
import com.example.beyond_may_be.course.service.CourseService;
import org.junit.jupiter.api.Test;

class ScheduleControllerTest {

  @Test
  void delegatesPlaceVisitToService() {
    CourseService courseService = mock(CourseService.class);
    ScheduleController controller = new ScheduleController(courseService);
    VisitPlaceRequest request = new VisitPlaceRequest(35.1595, 126.8526, true);
    VisitPlaceResponse expectedResponse =
        new VisitPlaceResponse("place_001", true, "1970-01-01T00:00:00", "temporary-user-id");

    when(courseService.visitPlace("schedule_01J", "place_001", request))
        .thenReturn(expectedResponse);

    ApiResponse<VisitPlaceResponse> response =
        controller.visitPlace("schedule_01J", "place_001", request);

    assertEquals(expectedResponse, response.getData());
    verify(courseService).visitPlace("schedule_01J", "place_001", request);
  }
}
