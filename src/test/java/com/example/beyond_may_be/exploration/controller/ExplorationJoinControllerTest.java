package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExplorationJoinControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class ExplorationJoinControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private ExplorationService explorationService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, explorationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(2L));
  }

  @Test
  void firstJoinReturnsCreatedResponse() throws Exception {
    given(explorationService.join(10L, 2L)).willReturn(joinResponse(false));

    mockMvc
        .perform(post("/api/v1/courses/10/join").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("생성에 성공했습니다."))
        .andExpect(jsonPath("$.code").value("COMMON201"))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.participantId").value(72))
        .andExpect(jsonPath("$.data.role").value("MEMBER"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.displayName").value("김감자감자 (2)"))
        .andExpect(jsonPath("$.data.locationSharingEnabled").value(false))
        .andExpect(jsonPath("$.data.joinedAt").value("2026-08-15T21:15:00+09:00"))
        .andExpect(jsonPath("$.data.alreadyJoined").value(false));
  }

  @Test
  void repeatedJoinReturnsOkResponse() throws Exception {
    given(explorationService.join(10L, 2L)).willReturn(joinResponse(true));

    mockMvc
        .perform(post("/api/v1/courses/10/join").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.alreadyJoined").value(true));
  }

  @Test
  void documentsBothJoinSuccessStatuses() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/join'].post.responses['200']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/join'].post.responses['201']").exists());
  }

  private ExplorationDtos.JoinResponse joinResponse(boolean alreadyJoined) {
    return new ExplorationDtos.JoinResponse(
        44L,
        72L,
        "MEMBER",
        "ACTIVE",
        "김감자감자 (2)",
        false,
        OffsetDateTime.parse("2026-08-15T21:15:00+09:00"),
        alreadyJoined);
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
