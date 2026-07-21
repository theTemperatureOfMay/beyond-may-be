package com.example.beyond_may_be.coreplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchLocationRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorePlaceSearchServiceTest {

  @Test
  void returnsTemporarySearchResult() {
    CorePlaceService service = new CorePlaceService();
    SearchRequest request =
        new SearchRequest(
            "course_01J",
            "양림동",
            List.of("CAFE", "HISTORY"),
            new SearchLocationRequest(35.1469, 126.9199),
            3000,
            20);

    SearchResponse response = service.searchPlaces(request);

    assertEquals(1, response.items().size());
    assertEquals("place_010", response.items().getFirst().placeId());
    assertEquals("HISTORY", response.items().getFirst().category());
    assertEquals(850, response.items().getFirst().distanceMeters());
    assertFalse(response.items().getFirst().alreadyInCourse());
  }
}
