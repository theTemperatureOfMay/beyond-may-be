package com.example.beyond_may_be.travelLog.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class VisitLogControllerStructureTest {

  @Test
  void declaresVisitLogEndpoints() {
    String controller = "com.example.beyond_may_be.travelLog.controller.VisitLogController";

    assertEndpoint(controller, "getVisitLogs", GetMapping.class, "/api/v1/visit-logs");
    assertEndpoint(controller, "createVisitLog", PostMapping.class, "/api/v1/visit-logs");
    assertEndpoint(controller, "getVisitLog", GetMapping.class, "/api/v1/visit-logs/{visitLogId}");
  }
}
