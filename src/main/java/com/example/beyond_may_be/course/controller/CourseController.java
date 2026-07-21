package com.example.beyond_may_be.course.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseResponse;
import com.example.beyond_may_be.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {
  private final CourseService courseService;

  @PostMapping("/ai-generation")
  public ApiResponse<AiGenerationResponse> requestAiGeneration(
      @RequestBody AiGenerationRequest request) {
    return ApiResponse.onSuccess(courseService.requestAiGeneration(request));
  }

  @PostMapping("/{courseId}/confirm")
  public ApiResponse<ConfirmResponse> confirmCourse(
      @PathVariable String courseId, @RequestBody ConfirmRequest request) {
    return ApiResponse.onSuccess(courseService.confirmCourse(courseId, request));
  }

  @GetMapping("/{courseId}")
  public ApiResponse<CourseResponse> getCourse(
      @PathVariable String courseId, @RequestParam(required = false) String include) {
    return ApiResponse.onSuccess(courseService.getCourse(courseId, include));
  }
}
