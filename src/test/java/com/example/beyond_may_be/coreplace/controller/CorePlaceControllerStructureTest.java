package com.example.beyond_may_be.coreplace.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class CorePlaceControllerStructureTest {

  @Test
  void declaresPlaceEndpoints() {
    assertEndpoint(
        "com.example.beyond_may_be.coreplace.controller.PlaceController",
        "getRecommendations",
        GetMapping.class,
        "/api/v1/places/recommendations");
    assertEndpoint(
        "com.example.beyond_may_be.coreplace.controller.PlaceController",
        "searchPlaces",
        PostMapping.class,
        "/api/v1/places/search");
    assertEndpoint(
        "com.example.beyond_may_be.coreplace.controller.ExplorationController",
        "getNearbyPlaces",
        GetMapping.class,
        "/api/exploration/{explorationId}/nearby-places");
  }
}
