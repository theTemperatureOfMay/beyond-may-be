package com.example.beyond_may_be.course.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

  private final CourseService courseService;

  @PostMapping("/{courseId}/confirm")
  public ApiResponse<CourseDtos.ConfirmResponse> confirm(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(courseService.confirm(courseId, userId));
  }

  @GetMapping("/{courseId}")
  public ApiResponse<CourseDtos.CourseDetailResponse> getCourseDetail(@PathVariable Long courseId) {
    return ApiResponse.onSuccess(courseService.getCourseDetail(courseId));
  }
}
