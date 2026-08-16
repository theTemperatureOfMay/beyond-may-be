package com.example.beyond_may_be.user.converter;

import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.dto.UserLoginResponseDto;
import com.example.beyond_may_be.user.dto.UserPreferenceResponseDto;
import com.example.beyond_may_be.user.dto.UserSignUpResponseDto;

public final class UserConverter {
  private UserConverter() {}

  public static UserSignUpResponseDto toSignUpResponse(User user, String token) {
    return new UserSignUpResponseDto(
        user.getId(),
        user.getNickname(),
        user.getIdentificationCode(),
        user.getPreferenceType(),
        token);
  }

  public static UserLoginResponseDto toLoginResponse(User user, String token) {
    return new UserLoginResponseDto(user.getId(), user.getNickname(), token);
  }

  public static UserPreferenceResponseDto toPreferenceResponse(User user) {
    return new UserPreferenceResponseDto(
        user.getId(),
        user.getNickname(),
        user.getPreferenceType(),
        user.getThinkerScore(),
        user.getFoodieScore(),
        user.getArtistScore(),
        user.getRemembererScore());
  }
}
