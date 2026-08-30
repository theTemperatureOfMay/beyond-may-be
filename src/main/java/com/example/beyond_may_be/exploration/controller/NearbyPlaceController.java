package com.example.beyond_may_be.exploration.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.exploration.service.NearbyPlaceService;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/explorations")
public class NearbyPlaceController {

  private final NearbyPlaceService nearbyPlaceService;

  @GetMapping("/{explorationId}/nearby-places")
  public ApiResponse<PlaceDtos.NearbyPlacesResponse> getNearbyPlaces(
      @PathVariable Long explorationId,
      @RequestParam
          @DecimalMin(value = "-90.0", message = "_BAD_REQUEST")
          @DecimalMax(value = "90.0", message = "_BAD_REQUEST")
          BigDecimal latitude,
      @RequestParam
          @DecimalMin(value = "-180.0", message = "_BAD_REQUEST")
          @DecimalMax(value = "180.0", message = "_BAD_REQUEST")
          BigDecimal longitude,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(
        nearbyPlaceService.getNearbyPlaces(explorationId, userId, latitude, longitude));
  }
}
