package com.example.beyond_may_be.user.dto;

public final class UserDtos {
  private UserDtos() {}

  public record NicknameRequest(String nickname, String resultToken) {}

  public record NicknameResponse(
      String userId, String nickname, String accessToken, String createdAt) {}

  public record LoginRequest(String nickname) {}

  public record LoginResponse(String userId, String nickname, String accessToken) {}

  public record PreferenceResponse(String preferenceType, String description, String testedAt) {}
}
