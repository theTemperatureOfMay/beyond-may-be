package com.example.beyond_may_be.course.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleResponse;
import com.example.beyond_may_be.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trip-schedules")
public class TripScheduleController {
  private final CourseService courseService;

  @PostMapping
  public ApiResponse<TripScheduleResponse> createTripSchedule(
      @RequestBody TripScheduleRequest request) {
    return ApiResponse.onSuccess(courseService.createTripSchedule(request));
  }
}
