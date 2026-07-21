package com.example.beyond_may_be.user.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.user.dto.UserDtos.LoginRequest;
import com.example.beyond_may_be.user.dto.UserDtos.LoginResponse;
import com.example.beyond_may_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
  private final UserService userService;

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
    return ApiResponse.onSuccess(userService.login(request));
  }
}
