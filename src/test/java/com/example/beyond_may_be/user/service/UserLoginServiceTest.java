package com.example.beyond_may_be.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.user.dto.UserDtos.LoginRequest;
import com.example.beyond_may_be.user.dto.UserDtos.LoginResponse;
import org.junit.jupiter.api.Test;

class UserLoginServiceTest {

  @Test
  void returnsTemporaryLoginResponse() {
    UserService service = new UserService();

    LoginResponse response = service.login(new LoginRequest("beyond"));

    assertEquals("temporary-user-id", response.userId());
    assertEquals("beyond", response.nickname());
    assertEquals("temporary-access-token", response.accessToken());
  }
}
