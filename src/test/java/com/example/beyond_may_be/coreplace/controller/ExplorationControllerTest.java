package com.example.beyond_may_be.coreplace.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.NearbyPlacesResponse;
import com.example.beyond_may_be.coreplace.service.CorePlaceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplorationControllerTest {

  @Test
  void delegatesNearbyPlaceQueryToService() {
    CorePlaceService corePlaceService = mock(CorePlaceService.class);
    ExplorationController controller = new ExplorationController(corePlaceService);
    NearbyPlacesResponse expectedResponse = new NearbyPlacesResponse(List.of());

    when(corePlaceService.getNearbyPlaces(1L, 35.1469, 126.9199)).thenReturn(expectedResponse);

    ApiResponse<NearbyPlacesResponse> response = controller.getNearbyPlaces(1L, 35.1469, 126.9199);

    assertEquals(expectedResponse, response.getData());
    verify(corePlaceService).getNearbyPlaces(1L, 35.1469, 126.9199);
  }
}
