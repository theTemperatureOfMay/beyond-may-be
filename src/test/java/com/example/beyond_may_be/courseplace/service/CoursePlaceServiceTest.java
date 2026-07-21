package com.example.beyond_may_be.courseplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitRequest;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;
import org.junit.jupiter.api.Test;

class CoursePlaceServiceTest {

  @Test
  void returnsTemporaryVisitResult() {
    CoursePlaceService service = new CoursePlaceService();
    VisitRequest request = new VisitRequest(35.1595, 126.8526, true);

    VisitResponse response = service.visitPlace("schedule_01J", "place_001", request);

    assertEquals("place_001", response.placeId());
    assertTrue(response.visited());
    assertEquals("1970-01-01T00:00:00", response.visitedAt());
    assertEquals("temporary-user-id", response.visitedBy());
  }
}
