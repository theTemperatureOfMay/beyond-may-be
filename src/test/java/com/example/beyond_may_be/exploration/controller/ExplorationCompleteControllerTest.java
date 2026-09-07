package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
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

@SpringBootTest(classes = ExplorationCompleteControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class ExplorationCompleteControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private ExplorationService explorationService;

  @BeforeEach
  void setUp() {
    reset(authTokenService, explorationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(9L));
  }

  @DisplayName("인증된 ACTIVE OWNER가 탐험을 조기 완료하면 200 응답을 반환한다.")
  @Test
  void completeEarly_activeOwner_returnsOk() throws Exception {
    given(explorationService.completeEarly(44L, 9L))
        .willReturn(
            new ExplorationDtos.CompleteResponse(
                44L,
                31L,
                "COMPLETED",
                "OWNER_EARLY_COMPLETION",
                OffsetDateTime.parse("2026-08-15T18:20:00+09:00"),
                new ExplorationDtos.CourseProgressResponse(3L, 5L, 60)));

    mockMvc
        .perform(
            post("/api/v1/explorations/44/complete").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.courseId").value(31))
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.completionReason").value("OWNER_EARLY_COMPLETION"))
        .andExpect(jsonPath("$.data.completedAt").value("2026-08-15T18:20:00+09:00"))
        .andExpect(jsonPath("$.data.courseProgress.completedCoursePlaceCount").value(3))
        .andExpect(jsonPath("$.data.courseProgress.totalCoursePlaceCount").value(5))
        .andExpect(jsonPath("$.data.courseProgress.completionRate").value(60))
        .andExpect(jsonPath("$.success").value(true));
  }

  @DisplayName("인증 정보가 없으면 탐험 조기 완료 요청을 거부한다.")
  @Test
  void completeEarly_withoutAuthentication_returnsUnauthorized() throws Exception {
    mockMvc.perform(post("/api/v1/explorations/44/complete")).andExpect(status().isUnauthorized());

    then(explorationService).shouldHaveNoInteractions();
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
