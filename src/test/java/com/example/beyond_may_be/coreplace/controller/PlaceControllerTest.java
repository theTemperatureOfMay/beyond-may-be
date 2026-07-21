package com.example.beyond_may_be.coreplace.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.PageInfoResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchLocationRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchResponse;
import com.example.beyond_may_be.coreplace.service.CorePlaceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceControllerTest {

  @Test
  void delegatesRecommendationQueryToService() {
    CorePlaceService corePlaceService = mock(CorePlaceService.class);
    PlaceController controller = new PlaceController(corePlaceService);
    RecommendationsRequest request =
        new RecommendationsRequest("schedule_01J", "cursor_01", 10, "place_999");
    RecommendationsResponse expectedResponse =
        new RecommendationsResponse(
            "schedule_01J",
            "SASEAK",
            "DAY_TRIP",
            3,
            2,
            10,
            List.of(),
            new PageInfoResponse(null, false));

    when(corePlaceService.getRecommendations(request)).thenReturn(expectedResponse);

    ApiResponse<RecommendationsResponse> response =
        controller.getRecommendations("schedule_01J", "cursor_01", 10, "place_999");

    assertEquals(expectedResponse, response.getData());
    verify(corePlaceService).getRecommendations(request);
  }

  @Test
  void delegatesPlaceSearchToService() {
    CorePlaceService corePlaceService = mock(CorePlaceService.class);
    PlaceController controller = new PlaceController(corePlaceService);
    SearchRequest request =
        new SearchRequest(
            "course_01J",
            "양림동",
            List.of("CAFE", "HISTORY"),
            new SearchLocationRequest(35.1469, 126.9199),
            3000,
            20);
    SearchResponse expectedResponse = new SearchResponse(List.of());

    when(corePlaceService.searchPlaces(request)).thenReturn(expectedResponse);

    ApiResponse<SearchResponse> response = controller.searchPlaces(request);

    assertEquals(expectedResponse, response.getData());
    verify(corePlaceService).searchPlaces(request);
  }
}
