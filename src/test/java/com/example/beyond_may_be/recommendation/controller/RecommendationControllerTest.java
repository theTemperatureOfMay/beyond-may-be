package com.example.beyond_may_be.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
import com.example.beyond_may_be.apiPayload.exception.handler.RecommendationHandler;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.config.SecurityConfig;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.recommendation.dto.RecommendationDtos;
import com.example.beyond_may_be.recommendation.service.RecommendationService;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = RecommendationControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class RecommendationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private RecommendationService recommendationService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, recommendationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(1L));
  }

  @Test
  void createsCurrentRecommendationSet() throws Exception {
    given(recommendationService.createOrGetCurrent(eq(1L), any()))
        .willReturn(
            new RecommendationDtos.RecommendationResponse(
                12L,
                TravelSchedule.ONE_NIGHT_TWO_DAYS,
                LocalDate.of(2099, 8, 20),
                LocalDate.of(2099, 8, 21),
                5,
                new RecommendationDtos.BatchResponse(
                    1,
                    List.of(
                        new RecommendationDtos.PlaceResponse(
                            101L, "국립아시아문화전당", "전시", List.of("복합문화공간"), null, null)))));

    mockMvc
        .perform(
            post("/api/v1/recommendations/sets")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "travelSchedule": "ONE_NIGHT_TWO_DAYS",
                      "startDate": "2099-08-20",
                      "endDate": "2099-08-21"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.recommendationId").value(12))
        .andExpect(jsonPath("$.data.minimumSelectionCount").value(5))
        .andExpect(jsonPath("$.data.batch.batchNumber").value(1))
        .andExpect(jsonPath("$.data.batch.places[0].placeId").value(101))
        .andExpect(jsonPath("$.data.batch.places[0].summary").doesNotExist());
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/recommendations/sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidPeriod() throws Exception {
    given(recommendationService.createOrGetCurrent(eq(1L), any()))
        .willThrow(new RecommendationHandler(ErrorStatus.RECOMMENDATION_INVALID_PERIOD));

    mockMvc
        .perform(
            post("/api/v1/recommendations/sets")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "travelSchedule": "ONE_NIGHT_TWO_DAYS",
                      "startDate": "2099-08-20",
                      "endDate": "2099-08-20"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION400"));
  }

  @Test
  void reportsMissingFinalPreferenceAsConflict() throws Exception {
    given(recommendationService.createOrGetCurrent(any(), any()))
        .willThrow(new RecommendationHandler(ErrorStatus.RECOMMENDATION_PREFERENCE_REQUIRED));

    mockMvc
        .perform(
            post("/api/v1/recommendations/sets")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "travelSchedule": "DAY_TRIP",
                      "startDate": "2099-08-20",
                      "endDate": "2099-08-20"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION409"));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, RecommendationController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    RecommendationService recommendationService() {
      return org.mockito.Mockito.mock(RecommendationService.class);
    }
  }
}
