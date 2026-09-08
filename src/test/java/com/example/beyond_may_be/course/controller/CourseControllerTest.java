package com.example.beyond_may_be.course.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.config.SecurityConfig;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.course.service.CourseService;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.async.WebAsyncTask;

@SpringBootTest(classes = CourseControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class CourseControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private CourseService courseService;
  @Autowired private CourseController courseController;
  @Autowired private ExplorationService explorationService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, courseService, explorationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(1L));
  }

  @Test
  void returnsExplorationIdByCourseId() throws Exception {
    given(explorationService.getIdByCourseId(31L, 1L))
        .willReturn(new ExplorationDtos.IdResponse(44L));

    mockMvc
        .perform(
            get("/api/v1/courses/31/exploration").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void rejectsUnauthenticatedExplorationIdLookup() throws Exception {
    mockMvc.perform(get("/api/v1/courses/31/exploration")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExplorationIdLookupByNonParticipant() throws Exception {
    given(explorationService.getIdByCourseId(31L, 1L))
        .willThrow(new ExplorationHandler(ErrorStatus._FORBIDDEN));

    mockMvc
        .perform(
            get("/api/v1/courses/31/exploration").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON403"));
  }

  @Test
  void returnsNotFoundWhenCourseHasNoExploration() throws Exception {
    given(explorationService.getIdByCourseId(31L, 1L))
        .willThrow(new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));

    mockMvc
        .perform(
            get("/api/v1/courses/31/exploration").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EXPLORATION404"));
  }

  @Test
  void documentsExplorationIdLookup() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/exploration'].get.responses['200']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/exploration'].get.responses['401']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/exploration'].get.responses['403']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/courses/{courseId}/exploration'].get.responses['404']")
                .exists());
  }

  @Test
  void generatesDraftCourseFromCurrentSelection() throws Exception {
    given(courseService.generate(1L))
        .willReturn(
            new CourseDtos.CourseDetailResponse(
                10L,
                "광주 여행",
                "DRAFT",
                "ONE_NIGHT_TWO_DAYS",
                LocalDate.of(2099, 8, 20),
                LocalDate.of(2099, 8, 21),
                LocalTime.of(7, 0),
                List.of()));

    MvcResult asyncResult =
        mockMvc
            .perform(
                post("/api/v1/courses/ai-generation").header("Authorization", "Bearer valid-token"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(asyncResult))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.courseId").value(10))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.startTime").value("07:00:00"));
  }

  @Test
  void limitsCourseGenerationToSixtySeconds() {
    assertThat(courseController.generate(1L))
        .isInstanceOfSatisfying(
            WebAsyncTask.class, task -> assertThat(task.getTimeout()).isEqualTo(60_000L));
  }

  @Test
  void rejectsUnauthenticatedCourseGeneration() throws Exception {
    mockMvc.perform(post("/api/v1/courses/ai-generation")).andExpect(status().isUnauthorized());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, CourseController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    CourseService courseService() {
      return org.mockito.Mockito.mock(CourseService.class);
    }

    @Bean
    ExplorationService explorationService() {
      return org.mockito.Mockito.mock(ExplorationService.class);
    }
  }
}
