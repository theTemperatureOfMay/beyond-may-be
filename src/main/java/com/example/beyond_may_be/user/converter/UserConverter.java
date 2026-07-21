package com.example.beyond_may_be.user.converter;

import com.example.beyond_may_be.user.dto.UserDtos.LoginResponse;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameResponse;
import com.example.beyond_may_be.user.dto.UserDtos.PreferenceResponse;

public final class UserConverter {
  private UserConverter() {}

  public static NicknameResponse toNicknameResponse(
      String userId, String nickname, String accessToken, String createdAt) {
    return new NicknameResponse(userId, nickname, accessToken, createdAt);
  }

  public static LoginResponse toLoginResponse(String userId, String nickname, String accessToken) {
    return new LoginResponse(userId, nickname, accessToken);
  }

  public static PreferenceResponse toPreferenceResponse(
      String preferenceType, String description, String testedAt) {
    return new PreferenceResponse(preferenceType, description, testedAt);
  }
}
