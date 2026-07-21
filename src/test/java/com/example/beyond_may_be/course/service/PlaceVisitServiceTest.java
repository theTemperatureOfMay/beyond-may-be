package com.example.beyond_may_be.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceResponse;
import org.junit.jupiter.api.Test;

class PlaceVisitServiceTest {

  @Test
  void returnsTemporaryVisitResult() {
    CourseService service = new CourseService();
    VisitPlaceRequest request = new VisitPlaceRequest(35.1595, 126.8526, true);

    VisitPlaceResponse response = service.visitPlace("schedule_01J", "place_001", request);

    assertEquals("place_001", response.placeId());
    assertTrue(response.visited());
    assertEquals("1970-01-01T00:00:00", response.visitedAt());
    assertEquals("temporary-user-id", response.visitedBy());
  }
}
