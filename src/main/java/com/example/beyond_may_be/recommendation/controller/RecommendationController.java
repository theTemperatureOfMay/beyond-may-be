package com.example.beyond_may_be.recommendation.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.recommendation.dto.RecommendationDtos;
import com.example.beyond_may_be.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

  private final RecommendationService recommendationService;

  @Operation(
      summary = "현재 추천 세트 조회",
      description = "인증된 사용자의 저장된 추천 회차와 서버에 확정된 좋아요·싫어요 상태를 조회합니다.")
  @GetMapping
  public ApiResponse<RecommendationDtos.CurrentRecommendationResponse> getCurrentRecommendation(
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.onSuccess(recommendationService.getCurrent(userId));
  }

  @Operation(
      summary = "현재 추천 세트 생성",
      description =
          "저장된 성향 점수로 추천하며, 유형별 장소가 부족하면 TourAPI 광주 변경분을 한 번 보충한 뒤 DB 후보를 사용합니다. 새 추천 장소의 빈 상세정보는 응답과 분리된 내부 작업으로 보강합니다.")
  @PostMapping("/sets")
  public ApiResponse<RecommendationDtos.RecommendationResponse> createRecommendationSet(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody RecommendationDtos.CreateRequest request) {
    // API 시작점: 인증된 사용자와 여행 일정을 추천 생성 흐름에 전달한다.
    return ApiResponse.onSuccess(recommendationService.createOrGetCurrent(userId, request));
  }

  @Operation(summary = "추천 회차 반응 교체", description = "현재 회차의 좋아요·싫어요 전체를 교체하고 필요한 다음 추천 회차를 반환합니다.")
  @PostMapping("/{batchNumber}/reactions")
  public ApiResponse<RecommendationDtos.ReactionResponse> replaceBatchReactions(
      @AuthenticationPrincipal Long userId,
      @PathVariable int batchNumber,
      @Valid @RequestBody RecommendationDtos.ReactionRequest request) {
    return ApiResponse.onSuccess(
        recommendationService.replaceBatchReactions(userId, batchNumber, request));
  }
}
