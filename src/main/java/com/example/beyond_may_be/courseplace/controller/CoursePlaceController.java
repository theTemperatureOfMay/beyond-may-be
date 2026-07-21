package com.example.beyond_may_be.courseplace.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitRequest;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;
import com.example.beyond_may_be.courseplace.service.CoursePlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules")
public class CoursePlaceController {
  private final CoursePlaceService coursePlaceService;

  @PatchMapping("/{scheduleId}/places/{placeId}/visit")
  public ApiResponse<VisitResponse> visitPlace(
      @PathVariable String scheduleId,
      @PathVariable String placeId,
      @RequestBody VisitRequest request) {
    return ApiResponse.onSuccess(coursePlaceService.visitPlace(scheduleId, placeId, request));
  }
}
