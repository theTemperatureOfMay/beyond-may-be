package com.example.beyond_may_be.exploration.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.apiPayload.code.status.SuccessStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

  @Operation(summary = "탐험 합류", description = "최초 합류는 201, 기존 참여자의 재요청 및 재합류는 200을 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기존 참여자의 재요청 또는 재합류"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "최초 합류")
  })
  @PostMapping("/courses/{courseId}/join")
  public ResponseEntity<ApiResponse<ExplorationDtos.JoinResponse>> join(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    ExplorationDtos.JoinResponse response = explorationService.join(courseId, userId);
    SuccessStatus status = response.alreadyJoined() ? SuccessStatus._OK : SuccessStatus._CREATED;
    return ResponseEntity.status(status.getHttpStatus()).body(ApiResponse.of(status, response));
  }

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
