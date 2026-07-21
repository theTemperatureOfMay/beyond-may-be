package com.example.beyond_may_be.coreplace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.NearbyPlacesResponse;
import org.junit.jupiter.api.Test;

class NearbyPlacesServiceTest {

  @Test
  void returnsTemporaryNearbyPlaces() {
    CorePlaceService service = new CorePlaceService();

    NearbyPlacesResponse response = service.getNearbyPlaces(1L, 35.1469, 126.9199);

    assertEquals(1, response.places().size());
    assertEquals("place_001", response.places().getFirst().placeId());
    assertEquals(320, response.places().getFirst().distanceMeters());
  }
}
