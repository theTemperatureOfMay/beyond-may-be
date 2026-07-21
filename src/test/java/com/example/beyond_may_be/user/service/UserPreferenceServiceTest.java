package com.example.beyond_may_be.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.user.dto.UserDtos.PreferenceResponse;
import org.junit.jupiter.api.Test;

class UserPreferenceServiceTest {

  @Test
  void returnsTemporaryPreference() {
    UserService service = new UserService();

    PreferenceResponse response = service.getPreference(1L);

    assertEquals("사색러", response.preferenceType());
    assertEquals("성향 검사 결과 설명입니다.", response.description());
    assertEquals("1970-01-01T00:00:00", response.testedAt());
  }
}
