package com.example.beyond_may_be.courseplace.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

class CoursePlaceControllerStructureTest {
  @Test
  void declaresVisitEndpoint() {
    assertEndpoint(
        "com.example.beyond_may_be.courseplace.controller.CoursePlaceController",
        "visitPlace",
        PatchMapping.class,
        "/api/schedules/{scheduleId}/places/{placeId}/visit");
  }
}
