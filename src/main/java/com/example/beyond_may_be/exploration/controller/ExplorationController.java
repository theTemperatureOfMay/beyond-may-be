package com.example.beyond_may_be.exploration.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ExplorationController {

  private final ExplorationService explorationService;

  @PostMapping("/courses/{courseId}/join")
  public ApiResponse<ExplorationDtos.JoinResponse> join(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.join(courseId, userId));
  }

  @GetMapping("/explorations/{explorationId}/members")
  public ApiResponse<ExplorationDtos.MemberListResponse> members(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.listMembers(explorationId, userId));
  }

  @PostMapping("/explorations/{explorationId}/start")
  public ApiResponse<ExplorationDtos.StartResponse> start(
      @PathVariable Long explorationId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.start(explorationId, userId));
  }
}
