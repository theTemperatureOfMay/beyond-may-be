package com.example.beyond_may_be.exploration.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExplorationController {

  private final ExplorationService explorationService;

  @GetMapping("/explorations")
  public ApiResponse<ExplorationDtos.ExplorationsResponse> getExplorations(
      @Parameter(schema = @Schema(allowableValues = {"ONGOING", "COMPLETED"})) @RequestParam
          ExplorationStatus status,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.getExplorations(userId, status));
  }

  @PostMapping("/explorations/{explorationId}/start")
  public ApiResponse<ExplorationDtos.StartResponse> start(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.start(explorationId, userId));
  }

  @PostMapping("/explorations/{explorationId}/complete")
  public ApiResponse<ExplorationDtos.CompleteResponse> complete(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.completeEarly(explorationId, userId));
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
