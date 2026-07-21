package com.example.beyond_may_be.question.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class PreferenceTestControllerStructureTest {
  private static final String CONTROLLER =
      "com.example.beyond_may_be.question.controller.PreferenceTestController";

  @Test
  void declaresPreferenceTestEndpoints() {
    assertEndpoint(CONTROLLER, "getQuestions", GetMapping.class, "/api/preference-test/questions");
    assertEndpoint(CONTROLLER, "saveResult", PostMapping.class, "/api/preference-test/result");
  }
}
