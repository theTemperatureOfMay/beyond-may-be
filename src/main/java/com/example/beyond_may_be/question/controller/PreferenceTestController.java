package com.example.beyond_may_be.question.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.QuestionsResponse;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultRequest;
import com.example.beyond_may_be.question.dto.PreferenceTestDtos.ResultResponse;
import com.example.beyond_may_be.question.service.PreferenceTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/preference-test")
public class PreferenceTestController {

  private final PreferenceTestService preferenceTestService;

  @GetMapping("/questions")
  public ApiResponse<QuestionsResponse> getQuestions() {
    return ApiResponse.onSuccess(preferenceTestService.getQuestions());
  }

  @PostMapping("/result")
  public ApiResponse<ResultResponse> saveResult(@RequestBody ResultRequest request) {
    return ApiResponse.onSuccess(preferenceTestService.saveResult(request));
  }
}
