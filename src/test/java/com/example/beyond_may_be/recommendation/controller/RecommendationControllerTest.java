package com.example.beyond_may_be.recommendation.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
  void getsCurrentRecommendationProgress() throws Exception {
    given(recommendationService.getCurrent(1L))
        .willReturn(
            new RecommendationDtos.CurrentRecommendationResponse(
                12L,
                TravelSchedule.ONE_NIGHT_TWO_DAYS,
                LocalDate.of(2099, 8, 20),
                LocalDate.of(2099, 8, 21),
                20,
                5,
                3,
                false,
                List.of(
                    new RecommendationDtos.BatchStateResponse(
                        1,
                        List.of(
                            new RecommendationDtos.PlaceResponse(
                                101L,
                                "국립아시아문화전당",
                                "문화시설",
                                List.of("전시", "문화"),
                                "검수된 장소 소개 문구",
                                null),
                            new RecommendationDtos.PlaceResponse(
                                102L,
                                "광주비엔날레전시관",
                                "문화시설",
                                List.of("비엔날레", "현대미술"),
                                null,
                                "https://example.com/places/102.webp")),
                        List.of(101L),
                        List.of(),
                        false))));

    mockMvc
        .perform(get("/api/v1/recommendations").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.recommendationId").value(12))
        .andExpect(jsonPath("$.data.travelSchedule").value("ONE_NIGHT_TWO_DAYS"))
        .andExpect(jsonPath("$.data.startDate").value("2099-08-20"))
        .andExpect(jsonPath("$.data.endDate").value("2099-08-21"))
        .andExpect(jsonPath("$.data.batchSize").value(20))
        .andExpect(jsonPath("$.data.minimumSelectionCount").value(5))
        .andExpect(jsonPath("$.data.selectedPlaceCount").value(3))
        .andExpect(jsonPath("$.data.selectionReady").value(false))
        .andExpect(jsonPath("$.data.batches[0].batchNumber").value(1))
        .andExpect(jsonPath("$.data.batches[0].places[0].placeId").value(101))
        .andExpect(jsonPath("$.data.batches[0].places[0].name").value("국립아시아문화전당"))
        .andExpect(jsonPath("$.data.batches[0].places[0].category").value("문화시설"))
        .andExpect(jsonPath("$.data.batches[0].places[0].tags[0]").value("전시"))
        .andExpect(jsonPath("$.data.batches[0].places[0].summary").value("검수된 장소 소개 문구"))
        .andExpect(jsonPath("$.data.batches[0].places[0].thumbnailUrl").hasJsonPath())
        .andExpect(jsonPath("$.data.batches[0].places[0].thumbnailUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.batches[0].places[1].summary").hasJsonPath())
        .andExpect(jsonPath("$.data.batches[0].places[1].summary").value(nullValue()))
        .andExpect(
            jsonPath("$.data.batches[0].places[1].thumbnailUrl")
                .value("https://example.com/places/102.webp"))
        .andExpect(jsonPath("$.data.batches[0].likedPlaceIds[0]").value(101))
        .andExpect(jsonPath("$.data.batches[0].dislikedPlaceIds").isEmpty())
        .andExpect(jsonPath("$.data.batches[0].completed").value(false));
  }

  @Test
  void reportsMissingCurrentRecommendationForLookup() throws Exception {
    given(recommendationService.getCurrent(1L))
        .willThrow(new RecommendationHandler(ErrorStatus.RECOMMENDATION_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/recommendations").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION404"));
  }

  @Test
  void rejectsUnauthenticatedCurrentRecommendationLookup() throws Exception {
    mockMvc.perform(get("/api/v1/recommendations")).andExpect(status().isUnauthorized());
  }

  @Test
  void documentsNullableRecommendationPlaceFields() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.PlaceResponse.properties.summary.type")
                .value(hasItem("null")))
        .andExpect(
            jsonPath("$.components.schemas.PlaceResponse.properties.thumbnailUrl.type")
                .value(hasItem("null")));
  }

  @Test
  void replacesBatchReactionsAndReturnsNextBatch() throws Exception {
    given(recommendationService.replaceBatchReactions(eq(1L), eq(1), any()))
        .willReturn(
            new RecommendationDtos.ReactionResponse(
                12L,
                1,
                1,
                3,
                false,
                true,
                new RecommendationDtos.BatchResponse(
                    2,
                    List.of(
                        new RecommendationDtos.PlaceResponse(
                            121L, "양림동 펭귄마을", "관광지", List.of("골목", "역사"), "검수된 소개", null)))));

    mockMvc
        .perform(
            post("/api/v1/recommendations/1/reactions")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "likedPlaceIds": [101],
                      "dislikedPlaceIds": [102]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.recommendationId").value(12))
        .andExpect(jsonPath("$.data.batchNumber").value(1))
        .andExpect(jsonPath("$.data.selectedPlaceCount").value(1))
        .andExpect(jsonPath("$.data.minimumSelectionCount").value(3))
        .andExpect(jsonPath("$.data.selectionReady").value(false))
        .andExpect(jsonPath("$.data.hasNextBatch").value(true))
        .andExpect(jsonPath("$.data.nextBatch.batchNumber").value(2))
        .andExpect(jsonPath("$.data.nextBatch.places[0].placeId").value(121));
  }

  @Test
  void rejectsMissingReactionArrays() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/recommendations/1/reactions")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"likedPlaceIds\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"));
  }

  @Test
  void reportsMissingRecommendationForReactions() throws Exception {
    given(recommendationService.replaceBatchReactions(eq(1L), eq(1), any()))
        .willThrow(new RecommendationHandler(ErrorStatus.RECOMMENDATION_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/v1/recommendations/1/reactions")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"likedPlaceIds\": [], \"dislikedPlaceIds\": []}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION404"));
  }

  @Test
  void reportsChangedRecommendationCompositionAsConflict() throws Exception {
    given(recommendationService.replaceBatchReactions(eq(1L), eq(1), any()))
        .willThrow(new RecommendationHandler(ErrorStatus.RECOMMENDATION_BATCH_CONFLICT));

    mockMvc
        .perform(
            post("/api/v1/recommendations/1/reactions")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"likedPlaceIds\": [], \"dislikedPlaceIds\": []}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECOMMENDATION409_2"));
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
