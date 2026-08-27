package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExplorationDetailControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class ExplorationDetailControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private ExplorationService explorationService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, explorationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(2L));
  }

  @Test
  void returnsExplorationDetail() throws Exception {
    given(explorationService.getDetail(44L, 2L))
        .willReturn(
            new ExplorationDtos.DetailResponse(
                44L,
                31L,
                "ONGOING",
                70L,
                OffsetDateTime.parse("2026-08-15T10:00:00+09:00"),
                null,
                4L,
                3L,
                new ExplorationDtos.CourseProgressResponse(2L, 5L, 40),
                new ExplorationDtos.CurrentParticipantResponse(72L, "MEMBER", "ACTIVE", false),
                new ExplorationDtos.PermissionsResponse(false, false)));

    mockMvc
        .perform(get("/api/v1/explorations/44").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.courseId").value(31))
        .andExpect(jsonPath("$.data.status").value("ONGOING"))
        .andExpect(jsonPath("$.data.startedByParticipantId").value(70))
        .andExpect(jsonPath("$.data.startedAt").value("2026-08-15T10:00:00+09:00"))
        .andExpect(jsonPath("$.data.completedAt").isEmpty())
        .andExpect(jsonPath("$.data.participantCount").value(4))
        .andExpect(jsonPath("$.data.teamVisitedPlaceCount").value(3))
        .andExpect(jsonPath("$.data.courseProgress.completedCoursePlaceCount").value(2))
        .andExpect(jsonPath("$.data.courseProgress.totalCoursePlaceCount").value(5))
        .andExpect(jsonPath("$.data.courseProgress.completionRate").value(40))
        .andExpect(jsonPath("$.data.currentParticipant.participantId").value(72))
        .andExpect(jsonPath("$.data.currentParticipant.role").value("MEMBER"))
        .andExpect(jsonPath("$.data.currentParticipant.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.currentParticipant.locationSharingEnabled").value(false))
        .andExpect(jsonPath("$.data.permissions.canStart").value(false))
        .andExpect(jsonPath("$.data.permissions.canCompleteEarly").value(false))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/v1/explorations/44")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsNonParticipant() throws Exception {
    given(explorationService.getDetail(44L, 2L))
        .willThrow(new ExplorationHandler(ErrorStatus._FORBIDDEN));

    mockMvc
        .perform(get("/api/v1/explorations/44").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON403"));
  }

  @Test
  void returnsNotFoundForMissingExploration() throws Exception {
    given(explorationService.getDetail(44L, 2L))
        .willThrow(new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/explorations/44").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EXPLORATION404"));
  }

  @Test
  void returnsInternalServerErrorWhenAggregationFails() throws Exception {
    given(explorationService.getDetail(44L, 2L)).willThrow(new IllegalStateException("집계 실패"));

    mockMvc
        .perform(get("/api/v1/explorations/44").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("COMMON500"));
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
