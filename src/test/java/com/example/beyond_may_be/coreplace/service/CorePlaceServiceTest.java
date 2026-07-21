package com.example.beyond_may_be.coreplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsResponse;
import org.junit.jupiter.api.Test;

class CorePlaceServiceTest {

  @Test
  void returnsTemporaryRecommendations() {
    CorePlaceService service = new CorePlaceService();
    RecommendationsRequest request =
        new RecommendationsRequest("schedule_01J", null, 10, "place_999");

    RecommendationsResponse response = service.getRecommendations(request);

    assertEquals("schedule_01J", response.scheduleId());
    assertEquals("SASEAK", response.personalityType());
    assertEquals("DAY_TRIP", response.durationType());
    assertEquals(3, response.minSelectablePlaceCount());
    assertEquals(2, response.selectedPlaceCount());
    assertEquals(10, response.recommendationBatchSize());
    assertEquals(1, response.items().size());
    assertEquals("place_001", response.items().getFirst().placeId());
    assertTrue(response.pageInfo().hasNext());
  }
}
