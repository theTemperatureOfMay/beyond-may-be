package com.example.beyond_may_be.coreplace.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchResponse;
import com.example.beyond_may_be.coreplace.service.CorePlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/places")
public class PlaceController {
  private final CorePlaceService corePlaceService;

  @GetMapping("/recommendations")
  public ApiResponse<RecommendationsResponse> getRecommendations(
      @RequestParam String scheduleId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String excludePlaceIds) {
    RecommendationsRequest request =
        new RecommendationsRequest(scheduleId, cursor, limit, excludePlaceIds);
    return ApiResponse.onSuccess(corePlaceService.getRecommendations(request));
  }

  @PostMapping("/search")
  public ApiResponse<SearchResponse> searchPlaces(@RequestBody SearchRequest request) {
    return ApiResponse.onSuccess(corePlaceService.searchPlaces(request));
  }
}
