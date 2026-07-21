package com.example.beyond_may_be.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleResponse;
import com.example.beyond_may_be.course.service.CourseService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TripScheduleControllerTest {

  @Test
  void delegatesTripScheduleCreationToService() {
    CourseService courseService = mock(CourseService.class);
    TripScheduleController controller = new TripScheduleController(courseService);
    TripScheduleRequest request =
        new TripScheduleRequest(
            TravelSchedule.DAY_TRIP, LocalDate.parse("2026-07-12"), LocalDate.parse("2026-07-12"));
    TripScheduleResponse expectedResponse =
        new TripScheduleResponse(
            "schedule_temporary",
            TravelSchedule.DAY_TRIP,
            3,
            10,
            10,
            OffsetDateTime.parse("2026-06-30T09:00:00+09:00"));

    when(courseService.createTripSchedule(request)).thenReturn(expectedResponse);

    ApiResponse<TripScheduleResponse> response = controller.createTripSchedule(request);

    assertEquals(expectedResponse, response.getData());
    verify(courseService).createTripSchedule(request);
  }
}
