package com.example.beyond_may_be.coreplace.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.NearbyPlacesResponse;
import com.example.beyond_may_be.coreplace.service.CorePlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exploration")
public class ExplorationController {
  private final CorePlaceService corePlaceService;

  @GetMapping("/{explorationId}/nearby-places")
  public ApiResponse<NearbyPlacesResponse> getNearbyPlaces(
      @PathVariable Long explorationId,
      @RequestParam double latitude,
      @RequestParam double longitude) {
    return ApiResponse.onSuccess(
        corePlaceService.getNearbyPlaces(explorationId, latitude, longitude));
  }
}
