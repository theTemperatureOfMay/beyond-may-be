package com.example.beyond_may_be.place.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/places")
public class PlaceController {

  private final PlaceService placeService;

  @Operation(summary = "장소 상세 조회", description = "활성 장소의 카탈로그 상세정보를 조회하고 비어 있는 설명과 운영시간을 보강합니다.")
  @GetMapping("/{placeId}")
  public ApiResponse<PlaceDtos.DetailResponse> getDetail(@PathVariable Long placeId) {
    return ApiResponse.onSuccess(placeService.getDetail(placeId));
  }
}
