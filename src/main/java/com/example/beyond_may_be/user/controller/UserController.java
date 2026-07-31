package com.example.beyond_may_be.user.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.user.dto.UserLoginRequestDto;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpRequestDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;
import com.example.beyond_may_be.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService userService;

  @PostMapping("/sign-up")
  public ApiResponse<UserSignUpResponseDto> signUp(@RequestBody UserSignUpRequestDto requestDto) {
    return ApiResponse.onSuccess(userService.signUp(requestDto));
  }

  @PostMapping("/login")
  public ApiResponse<UserLoginResponseDto> login(@RequestBody UserLoginRequestDto requestDto) {
    return ApiResponse.onSuccess(userService.login(requestDto));
  }
}
