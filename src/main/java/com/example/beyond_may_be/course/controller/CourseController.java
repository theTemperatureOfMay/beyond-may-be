package com.example.beyond_may_be.course.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.code.status.SuccessStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.course.service.CourseService;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

  private static final long COURSE_GENERATION_TIMEOUT_MILLIS = 60_000L;
  private static final String ACTIVE_EXPLORATION_ERROR =
      """
      {"message":"이미 다른 탐험에 참여 중입니다.","code":"EXPLORATION409","data":{"activeExplorationId":44},"success":false}
      """;

  private final CourseService courseService;
  private final ExplorationService explorationService;

  @PostMapping("/ai-generation")
  public WebAsyncTask<ApiResponse<CourseDtos.CourseDetailResponse>> generate(
      @AuthenticationPrincipal Long userId) {
    WebAsyncTask<ApiResponse<CourseDtos.CourseDetailResponse>> task =
        new WebAsyncTask<>(
            COURSE_GENERATION_TIMEOUT_MILLIS,
            () -> ApiResponse.onSuccess(courseService.generate(userId)));
    task.onTimeout(
        () -> {
          throw new CourseHandler(ErrorStatus.COURSE_GENERATION_TIMEOUT);
        });
    return task;
  }

  @PostMapping("/{courseId}/confirm")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "코스 확정 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "다른 활성 탐험 참여 중(EXPLORATION409) 또는 이미 확정된 코스(COURSE409)",
        content =
            @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "application/json",
                examples =
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "다른 활성 탐험 참여 중",
                        value = ACTIVE_EXPLORATION_ERROR)))
  })
  public ApiResponse<CourseDtos.ConfirmResponse> confirm(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(courseService.confirm(courseId, userId));
  }

  @GetMapping("/{courseId}")
  public ApiResponse<CourseDtos.CourseDetailResponse> getCourseDetail(@PathVariable Long courseId) {
    return ApiResponse.onSuccess(courseService.getCourseDetail(courseId));
  }

  @Operation(
      summary = "코스별 탐험 ID 조회",
      description = "인증된 현재 또는 과거 참여자가 코스에 연결된 탐험 ID를 조회합니다. 공유 링크 만료와 탐험 상태에 관계없이 조회할 수 있습니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "탐험 ID"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "인증 필요"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "탐험 참여자가 아님"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "코스에 연결된 탐험 없음")
  })
  @GetMapping("/{courseId}/exploration")
  public ApiResponse<ExplorationDtos.IdResponse> getExplorationId(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(explorationService.getIdByCourseId(courseId, userId));
  }

  @Operation(summary = "탐험 합류", description = "최초 합류는 201, 기존 참여자의 재요청 및 재합류는 200을 반환합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "기존 참여자의 재요청 또는 재합류"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "최초 합류"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "다른 활성 탐험 참여 중(EXPLORATION409) 또는 완료된 탐험(EXPLORATION409_2)",
        content =
            @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "application/json",
                examples = {
                  @io.swagger.v3.oas.annotations.media.ExampleObject(
                      name = "다른 활성 탐험 참여 중",
                      value = ACTIVE_EXPLORATION_ERROR),
                  @io.swagger.v3.oas.annotations.media.ExampleObject(
                      name = "완료된 탐험",
                      value =
                          "{\"message\":\"이미 완료된 탐험입니다.\",\"code\":\"EXPLORATION409_2\",\"data\":null,\"success\":false}")
                }))
  })
  @PostMapping("/{courseId}/join")
  public ResponseEntity<ApiResponse<ExplorationDtos.JoinResponse>> join(
      @PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
    ExplorationDtos.JoinResponse response = explorationService.join(courseId, userId);
    SuccessStatus status = response.alreadyJoined() ? SuccessStatus._OK : SuccessStatus._CREATED;
    return ResponseEntity.status(status.getHttpStatus()).body(ApiResponse.of(status, response));
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
