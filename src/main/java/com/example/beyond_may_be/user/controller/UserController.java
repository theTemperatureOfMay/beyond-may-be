package com.example.beyond_may_be.user.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameRequest;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameResponse;
import com.example.beyond_may_be.user.dto.UserDtos.PreferenceResponse;
import com.example.beyond_may_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;

  @PostMapping("/nickname")
  public ApiResponse<NicknameResponse> createNickname(@RequestBody NicknameRequest request) {
    return ApiResponse.onSuccess(userService.createNickname(request));
  }

  @GetMapping("/{userId}/preference")
  public ApiResponse<PreferenceResponse> getPreference(@PathVariable Long userId) {
    return ApiResponse.onSuccess(userService.getPreference(userId));
  }
}
