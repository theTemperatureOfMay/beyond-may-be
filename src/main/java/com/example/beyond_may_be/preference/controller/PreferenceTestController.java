package com.example.beyond_may_be.preference.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.preference.dto.PreferenceTestDtos.QuestionsResponse;
import com.example.beyond_may_be.preference.service.PreferenceTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/preference-tests")
public class PreferenceTestController {

  private final PreferenceTestService preferenceTestService;

  @GetMapping("/questions")
  public ApiResponse<QuestionsResponse> getQuestions() {
    return ApiResponse.onSuccess(preferenceTestService.getQuestions());
  }
}
