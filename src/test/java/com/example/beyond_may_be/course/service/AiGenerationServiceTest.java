package com.example.beyond_may_be.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiGenerationServiceTest {

  @Test
  void returnsTemporaryAiGeneratedCourse() {
    CourseService service = new CourseService();
    AiGenerationRequest request =
        new AiGenerationRequest(
            "schedule_01J",
            List.of("place_001", "place_002", "place_003"),
            "09:00",
            List.of("WALK", "TRANSIT", "TAXI"));

    AiGenerationResponse response = service.requestAiGeneration(request);

    assertEquals("course_temporary", response.courseId());
    assertEquals("DRAFT", response.status());
    assertEquals("하루치 광주", response.title());
    assertEquals(TravelSchedule.DAY_TRIP, response.durationType());
    assertEquals(3, response.totalPlaceCount());
    assertEquals(360, response.estimatedDurationMinutes());
    assertEquals(8200, response.estimatedDistanceMeters());
    assertEquals("place_001", response.places().getFirst().placeId());
    assertEquals("KAKAO", response.route().mapProvider());
    assertEquals(OffsetDateTime.parse("2026-06-30T09:15:00+09:00"), response.createdAt());
  }
}
