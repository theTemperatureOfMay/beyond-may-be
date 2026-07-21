package com.example.beyond_may_be.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.user.dto.UserDtos.NicknameRequest;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameResponse;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  @Test
  void returnsTemporaryNicknameResponse() {
    UserService service = new UserService();

    NicknameResponse response =
        service.createNickname(new NicknameRequest("beyond", "result-token"));

    assertEquals("temporary-user-id", response.userId());
    assertEquals("beyond", response.nickname());
    assertEquals("temporary-access-token", response.accessToken());
    assertEquals("1970-01-01T00:00:00", response.createdAt());
  }
}
