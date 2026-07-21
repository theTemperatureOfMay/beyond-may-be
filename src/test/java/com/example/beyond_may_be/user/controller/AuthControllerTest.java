package com.example.beyond_may_be.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.user.dto.UserDtos.LoginRequest;
import com.example.beyond_may_be.user.dto.UserDtos.LoginResponse;
import com.example.beyond_may_be.user.service.UserService;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

  @Test
  void delegatesLoginToService() {
    UserService userService = mock(UserService.class);
    AuthController controller = new AuthController(userService);
    LoginRequest request = new LoginRequest("beyond");
    LoginResponse expectedResponse =
        new LoginResponse("temporary-user-id", "beyond", "temporary-access-token");

    when(userService.login(request)).thenReturn(expectedResponse);

    ApiResponse<LoginResponse> response = controller.login(request);

    assertEquals(expectedResponse, response.getData());
    verify(userService).login(request);
  }
}
