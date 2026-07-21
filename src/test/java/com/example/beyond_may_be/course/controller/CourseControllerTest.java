package com.example.beyond_may_be.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.RouteResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.ShareResponse;
import com.example.beyond_may_be.course.service.CourseService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseControllerTest {

  @Test
  void delegatesAiGenerationToService() {
    CourseService courseService = mock(CourseService.class);
    CourseController controller = new CourseController(courseService);
    AiGenerationRequest request =
        new AiGenerationRequest("schedule_01J", List.of("place_001"), "09:00", List.of("WALK"));
    AiGenerationResponse expectedResponse =
        new AiGenerationResponse(
            "course_temporary",
            "DRAFT",
            "하루치 광주",
            TravelSchedule.DAY_TRIP,
            1,
            360,
            8200,
            List.of(),
            new RouteResponse("encoded-polyline", "KAKAO"),
            OffsetDateTime.parse("2026-06-30T09:15:00+09:00"));

    when(courseService.requestAiGeneration(request)).thenReturn(expectedResponse);

    ApiResponse<AiGenerationResponse> response = controller.requestAiGeneration(request);

    assertEquals(expectedResponse, response.getData());
    verify(courseService).requestAiGeneration(request);
  }

  @Test
  void delegatesCourseConfirmationToService() {
    CourseService courseService = mock(CourseService.class);
    CourseController controller = new CourseController(courseService);
    ConfirmRequest request = new ConfirmRequest(true);
    ConfirmResponse expectedResponse =
        new ConfirmResponse(
            "course_01J",
            "CONFIRMED",
            "sess_temporary",
            new ShareResponse(
                "share_temporary",
                "https://gwangju.app/explore/course_01J",
                OffsetDateTime.parse("2026-07-03T09:20:00+09:00"),
                5),
            OffsetDateTime.parse("2026-06-30T09:20:00+09:00"));

    when(courseService.confirmCourse("course_01J", request)).thenReturn(expectedResponse);

    ApiResponse<ConfirmResponse> response = controller.confirmCourse("course_01J", request);

    assertEquals(expectedResponse, response.getData());
    verify(courseService).confirmCourse("course_01J", request);
  }

  @Test
  void delegatesCourseDetailQueryToService() {
    CourseService courseService = mock(CourseService.class);
    CourseController controller = new CourseController(courseService);
    CourseResponse expectedResponse =
        new CourseResponse(
            "course_01J",
            "하루치 광주",
            "IN_PROGRESS",
            TravelSchedule.DAY_TRIP,
            "sess_temporary",
            "MEMBER",
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            null);

    when(courseService.getCourse("course_01J", "places,team")).thenReturn(expectedResponse);

    ApiResponse<CourseResponse> response = controller.getCourse("course_01J", "places,team");

    assertEquals(expectedResponse, response.getData());
    verify(courseService).getCourse("course_01J", "places,team");
  }
}
