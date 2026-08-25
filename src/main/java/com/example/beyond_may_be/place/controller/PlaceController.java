package com.example.beyond_may_be.place.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceRecommendationResponse;
import com.example.beyond_may_be.place.service.PlaceService;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/places")
public class PlaceController {

  private final PlaceService placeService;

  @GetMapping("/recommendations")
  public ApiResponse<PlaceRecommendationResponse> getRecommendations(
      @RequestParam TravelPreferenceType type) {
    return ApiResponse.onSuccess(placeService.getRecommendations(type));
  }
}
