package com.example.beyond_may_be.course.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CompleteResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceResponse;
import com.example.beyond_may_be.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules")
public class ScheduleController {
  private final CourseService courseService;

  @PatchMapping("/{scheduleId}/course/complete")
  public ApiResponse<CompleteResponse> completeCourse(@PathVariable Long scheduleId) {
    return ApiResponse.onSuccess(courseService.completeCourse(scheduleId));
  }

  @PatchMapping("/{scheduleId}/places/{placeId}/visit")
  public ApiResponse<VisitPlaceResponse> visitPlace(
      @PathVariable String scheduleId,
      @PathVariable String placeId,
      @RequestBody VisitPlaceRequest request) {
    return ApiResponse.onSuccess(courseService.visitPlace(scheduleId, placeId, request));
  }
}
