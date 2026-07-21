package com.example.beyond_may_be.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameRequest;
import com.example.beyond_may_be.user.dto.UserDtos.NicknameResponse;
import com.example.beyond_may_be.user.dto.UserDtos.PreferenceResponse;
import com.example.beyond_may_be.user.service.UserService;
import org.junit.jupiter.api.Test;

class UserControllerTest {

  @Test
  void delegatesNicknameCreationToService() {
    UserService userService = mock(UserService.class);
    UserController controller = new UserController(userService);
    NicknameRequest request = new NicknameRequest("beyond", "result-token");
    NicknameResponse expectedResponse =
        new NicknameResponse(
            "temporary-user-id", "beyond", "temporary-access-token", "1970-01-01T00:00:00");

    when(userService.createNickname(request)).thenReturn(expectedResponse);

    ApiResponse<NicknameResponse> response = controller.createNickname(request);

    assertEquals(expectedResponse, response.getData());
    verify(userService).createNickname(request);
  }

  @Test
  void delegatesPreferenceQueryToService() {
    UserService userService = mock(UserService.class);
    UserController controller = new UserController(userService);
    PreferenceResponse expectedResponse =
        new PreferenceResponse("사색러", "성향 검사 결과 설명입니다.", "1970-01-01T00:00:00");

    when(userService.getPreference(1L)).thenReturn(expectedResponse);

    ApiResponse<PreferenceResponse> response = controller.getPreference(1L);

    assertEquals(expectedResponse, response.getData());
    verify(userService).getPreference(1L);
  }
}
