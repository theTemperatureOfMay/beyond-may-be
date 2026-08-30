package com.example.beyond_may_be.exploration.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExplorationController {

  private final ExplorationService explorationService;

  @PostMapping("/explorations/{explorationId}/start")
  public ApiResponse<ExplorationDtos.StartResponse> start(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.start(explorationId, userId));
  }

  @GetMapping("/explorations/{explorationId}/participants")
  public ApiResponse<ExplorationDtos.ParticipantsResponse> participants(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.getParticipants(explorationId, userId));
  }

  @GetMapping("/explorations/{explorationId}")
  public ApiResponse<ExplorationDtos.DetailResponse> getDetail(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.getDetail(explorationId, userId));
  }

  @PatchMapping("/explorations/{explorationId}/participants/me/location-sharing")
  public ApiResponse<ExplorationDtos.LocationSharingResponse> updateLocationSharing(
      @PathVariable Long explorationId,
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ExplorationDtos.LocationSharingUpdateRequest request) {
    return ApiResponse.onSuccess(
        explorationService.updateLocationSharing(explorationId, userId, request.enabled()));
  }
}
