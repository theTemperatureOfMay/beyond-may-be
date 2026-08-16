package com.example.beyond_may_be.visit.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.service.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/explorations/{explorationId}/places/{placeId}/visits")
public class VisitController {

  private final VisitService visitService;

  @PostMapping
  public ApiResponse<VisitDtos.ConfirmResponse> confirm(
      @PathVariable Long explorationId,
      @PathVariable Long placeId,
      @RequestBody VisitDtos.ConfirmRequest request,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(
        visitService.confirmVisit(
            explorationId, userId, placeId, request.latitude(), request.longitude()));
  }
}
