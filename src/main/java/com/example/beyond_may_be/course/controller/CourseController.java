package com.example.beyond_may_be.course.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @GetMapping("/{courseId}/draft")
  public ApiResponse<CourseDtos.CourseDetailResponse> getDraftDetail(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(courseService.getDraftDetail(courseId, userId));
  }

  @PutMapping("/{courseId}/places")
  public ApiResponse<CourseDtos.CourseDetailResponse> editPlaces(
      @PathVariable Long courseId,
      @AuthenticationPrincipal Long userId,
      @RequestBody CourseDtos.UpdatePlacesRequest request) {
    return ApiResponse.onSuccess(courseService.editPlaces(courseId, userId, request));
  }

  @PostMapping("/{courseId}/chat")
  public ApiResponse<CourseDtos.ChatResponse> requestChatRevision(
      @PathVariable Long courseId,
      @AuthenticationPrincipal Long userId,
      @RequestBody CourseDtos.ChatRequest request) {
    return ApiResponse.onSuccess(courseService.requestChatRevision(courseId, userId, request));
  }

  @PostMapping("/{courseId}/chat/apply")
  public ApiResponse<CourseDtos.CourseDetailResponse> applyChatRevision(
      @PathVariable Long courseId,
      @AuthenticationPrincipal Long userId,
      @RequestBody CourseDtos.ApplyChatRevisionRequest request) {
    return ApiResponse.onSuccess(courseService.applyChatRevision(courseId, userId, request));
  }

  @PostMapping("/{courseId}/places/{placeId}")
  public ApiResponse<CourseDtos.CourseDetailResponse> addRecommendedPlace(
      @PathVariable Long courseId,
      @PathVariable Long placeId,
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(courseService.addRecommendedPlace(courseId, userId, placeId));
  }
}
