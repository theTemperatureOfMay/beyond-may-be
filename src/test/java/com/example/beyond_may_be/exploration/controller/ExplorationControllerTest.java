package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExplorationControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class ExplorationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private ExplorationService explorationService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, explorationService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(71L));
  }

  @Test
  void returnsCurrentParticipants() throws Exception {
    given(explorationService.getParticipants(44L, 71L))
        .willReturn(
            new ExplorationDtos.ParticipantsResponse(
                44L,
                2,
                List.of(
                    new ExplorationDtos.ParticipantResponse(
                        70L,
                        "여행자",
                        "OWNER",
                        "ACTIVE",
                        3L,
                        true,
                        false,
                        OffsetDateTime.parse("2026-08-14T18:00:00+09:00")),
                    new ExplorationDtos.ParticipantResponse(
                        71L,
                        "별밤지기",
                        "MEMBER",
                        "ACTIVE",
                        2L,
                        false,
                        true,
                        OffsetDateTime.parse("2026-08-15T09:40:00+09:00")))));

    mockMvc
        .perform(
            get("/api/v1/explorations/44/participants")
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.participantCount").value(2))
        .andExpect(jsonPath("$.data.participants[0].participantId").value(70))
        .andExpect(jsonPath("$.data.participants[0].displayName").value("여행자"))
        .andExpect(jsonPath("$.data.participants[0].role").value("OWNER"))
        .andExpect(jsonPath("$.data.participants[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.participants[0].visitedPlaceCount").value(3))
        .andExpect(jsonPath("$.data.participants[0].locationSharingEnabled").value(true))
        .andExpect(jsonPath("$.data.participants[0].isMe").value(false))
        .andExpect(jsonPath("$.data.participants[0].joinedAt").value("2026-08-14T18:00:00+09:00"))
        .andExpect(jsonPath("$.data.participants[0].userId").doesNotExist())
        .andExpect(jsonPath("$.data.participants[0].latitude").doesNotExist())
        .andExpect(jsonPath("$.data.participants[0].longitude").doesNotExist())
        .andExpect(jsonPath("$.data.participants[1].isMe").value(true));
  }

  @Test
  void rejectsMissingLocationSharingEnabled() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/explorations/44/participants/me/location-sharing")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"enabled\":\"true\"}", "{\"enabled\":1}"})
  void rejectsNonBooleanLocationSharingEnabled(String requestBody) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/explorations/44/participants/me/location-sharing")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void updatesCurrentParticipantsLocationSharing() throws Exception {
    given(explorationService.updateLocationSharing(44L, 71L, true))
        .willReturn(
            new ExplorationDtos.LocationSharingResponse(
                44L, 71L, true, OffsetDateTime.parse("2026-08-15T21:20:00+09:00")));

    mockMvc
        .perform(
            patch("/api/v1/explorations/44/participants/me/location-sharing")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.participantId").value(71))
        .andExpect(jsonPath("$.data.locationSharingEnabled").value(true))
        .andExpect(jsonPath("$.data.updatedAt").value("2026-08-15T21:20:00+09:00"))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void rejectsUnauthenticatedLocationSharingRequest() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/explorations/44/participants/me/location-sharing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInactiveParticipantLocationSharingRequest() throws Exception {
    given(explorationService.updateLocationSharing(44L, 71L, true))
        .willThrow(new ExplorationHandler(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN));

    mockMvc
        .perform(
            patch("/api/v1/explorations/44/participants/me/location-sharing")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EXPLORATION403"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc
        .perform(get("/api/v1/explorations/44/participants"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsNonParticipant() throws Exception {
    given(explorationService.getParticipants(44L, 71L))
        .willThrow(new ExplorationHandler(ErrorStatus._FORBIDDEN));

    mockMvc
        .perform(
            get("/api/v1/explorations/44/participants")
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COMMON403"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void reportsMissingExploration() throws Exception {
    given(explorationService.getParticipants(44L, 71L))
        .willThrow(new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));

    mockMvc
        .perform(
            get("/api/v1/explorations/44/participants")
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EXPLORATION404"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void reportsParticipantQueryFailure() throws Exception {
    given(explorationService.getParticipants(44L, 71L))
        .willThrow(new IllegalStateException("database failure"));

    mockMvc
        .perform(
            get("/api/v1/explorations/44/participants")
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("COMMON500"))
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
