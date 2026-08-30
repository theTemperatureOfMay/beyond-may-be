package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.config.SecurityConfig;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExplorationStartControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class ExplorationStartControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private ExplorationService explorationService;

  @BeforeEach
  void setUp() {
    reset(authTokenService, explorationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(9L));
  }

  @DisplayName("인증된 활성 참여자가 탐험을 시작하면 200 응답을 반환한다.")
  @Test
  void start_activeParticipant_returnsOk() throws Exception {
    given(explorationService.start(44L, 9L))
        .willReturn(
            new ExplorationDtos.StartResponse(
                44L, 31L, "ONGOING", 72L, OffsetDateTime.parse("2026-08-15T10:00:00+09:00")));

    mockMvc
        .perform(
            post("/api/v1/explorations/44/start").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.courseId").value(31))
        .andExpect(jsonPath("$.data.status").value("ONGOING"))
        .andExpect(jsonPath("$.data.participantId").value(72))
        .andExpect(jsonPath("$.data.startedAt").value("2026-08-15T10:00:00+09:00"));
  }

  @DisplayName("인증 정보가 없으면 탐험 시작 요청을 거부한다.")
  @Test
  void start_withoutAuthentication_returnsUnauthorized() throws Exception {
    mockMvc.perform(post("/api/v1/explorations/44/start")).andExpect(status().isUnauthorized());

    then(explorationService).shouldHaveNoInteractions();
  }

  @DisplayName("이미 시작했거나 완료된 탐험은 409를 반환한다.")
  @Test
  void start_alreadyStarted_returnsConflict() throws Exception {
    given(explorationService.start(44L, 9L))
        .willThrow(new ExplorationHandler(ErrorStatus.EXPLORATION_START_CONFLICT));

    mockMvc
        .perform(
            post("/api/v1/explorations/44/start").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EXPLORATION409_3"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, ExplorationController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    ExplorationService explorationService() {
      return org.mockito.Mockito.mock(ExplorationService.class);
    }
  }
}
