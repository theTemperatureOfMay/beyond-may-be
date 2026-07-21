package com.example.beyond_may_be.courseplace.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitRequest;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
public class CoursePlaceController {
  @PatchMapping("/{scheduleId}/places/{placeId}/visit")
  public ApiResponse<VisitResponse> visitPlace(
      @PathVariable Long scheduleId,
      @PathVariable Long placeId,
      @RequestBody VisitRequest request) {
    throw new UnsupportedOperationException("Not implemented");
  }
}
