package com.example.beyond_may_be.user.service;

import com.example.beyond_may_be.user.converter.UserConverter;
import com.example.beyond_may_be.user.dto.UserDtos.LoginRequest;
import com.example.beyond_may_be.user.dto.UserDtos.LoginResponse;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameRequest;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameResponse;
import com.example.beyond_may_be.user.dto.UserDtos.PreferenceResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  public NicknameResponse createNickname(NicknameRequest request) {
    return UserConverter.toNicknameResponse(
        "temporary-user-id", request.nickname(), "temporary-access-token", "1970-01-01T00:00:00");
  }

  public LoginResponse login(LoginRequest request) {
    return UserConverter.toLoginResponse(
        "temporary-user-id", request.nickname(), "temporary-access-token");
  }

  public PreferenceResponse getPreference(Long userId) {
    return UserConverter.toPreferenceResponse("사색러", "성향 검사 결과 설명입니다.", "1970-01-01T00:00:00");
  }
}
