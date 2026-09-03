package com.example.beyond_may_be.visit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.config.SecurityConfig;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.service.VisitService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@SpringBootTest(classes = VisitControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class VisitControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private VisitService visitService;

  @BeforeEach
  void setUp() {
    reset(authTokenService, visitService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(9L));
  }

  @DisplayName("방문 인증에 성공하면 생성된 방문과 팀 진행 상태를 201로 반환한다.")
  @Test
  void confirmVisit_validRequest_returnsCreated() throws Exception {
    OffsetDateTime visitedAt = OffsetDateTime.parse("2026-08-15T14:32:10+09:00");
    given(visitService.confirmVisit(any(VisitDtos.ConfirmRequest.class), any(Long.class)))
        .willReturn(
            new VisitDtos.ConfirmResponse(
                9001L,
                44L,
                72L,
                121L,
                null,
                false,
                visitedAt,
                37L,
                true,
                new ExplorationDtos.CourseProgressResponse(2L, 5L, 40),
                "ONGOING"));

    mockMvc
        .perform(
            post("/api/v1/visits")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "explorationId": 44,
                      "placeId": 121,
                      "latitude": 35.1402,
                      "longitude": 126.9124,
                      "accuracyMeters": 18.5
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("생성에 성공했습니다."))
        .andExpect(jsonPath("$.code").value("COMMON201"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.visitId").value(9001))
        .andExpect(jsonPath("$.data.explorationId").value(44))
        .andExpect(jsonPath("$.data.participantId").value(72))
        .andExpect(jsonPath("$.data.placeId").value(121))
        .andExpect(jsonPath("$.data.coursePlaceId").isEmpty())
        .andExpect(jsonPath("$.data.isCoursePlace").value(false))
        .andExpect(jsonPath("$.data.visitedAt").value("2026-08-15T14:32:10+09:00"))
        .andExpect(jsonPath("$.data.distanceMeters").value(37))
        .andExpect(jsonPath("$.data.teamFirstVisit").value(true))
        .andExpect(jsonPath("$.data.courseProgress.completedCoursePlaceCount").value(2))
        .andExpect(jsonPath("$.data.courseProgress.totalCoursePlaceCount").value(5))
        .andExpect(jsonPath("$.data.courseProgress.completionRate").value(40))
        .andExpect(jsonPath("$.data.explorationStatus").value("ONGOING"));
  }

  @DisplayName("방문 사진을 첨부하면 저장 결과와 서명 URL을 201로 반환한다.")
  @Test
  void attachPhoto_validImage_returnsCreated() throws Exception {
    OffsetDateTime uploadedAt = OffsetDateTime.parse("2026-08-15T14:33:00+09:00");
    OffsetDateTime urlExpiresAt = OffsetDateTime.parse("2026-08-15T15:33:00+09:00");
    MockMultipartFile file =
        new MockMultipartFile("file", "visit.webp", "image/webp", "image".getBytes());
    given(visitService.attachPhoto(9001L, file, 9L))
        .willReturn(
            new VisitDtos.PhotoResponse(
                501L, 9001L, 1, "https://example.com/signed/501", urlExpiresAt, uploadedAt));

    mockMvc
        .perform(
            multipart("/api/v1/visits/{visitId}/photos", 9001L)
                .file(file)
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("생성에 성공했습니다."))
        .andExpect(jsonPath("$.code").value("COMMON201"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.visitPhotoId").value(501))
        .andExpect(jsonPath("$.data.visitId").value(9001))
        .andExpect(jsonPath("$.data.displayOrder").value(1))
        .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/signed/501"))
        .andExpect(jsonPath("$.data.urlExpiresAt").value("2026-08-15T15:33:00+09:00"))
        .andExpect(jsonPath("$.data.uploadedAt").value("2026-08-15T14:33:00+09:00"));
  }

  @DisplayName("사진 파일이 누락되면 공통 400 응답으로 거부한다.")
  @Test
  void attachPhoto_missingFile_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/visits/{visitId}/photos", 9001L)
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"))
        .andExpect(jsonPath("$.success").value(false));

    then(visitService).shouldHaveNoInteractions();
  }

  @DisplayName("multipart 제한을 넘은 사진은 공통 413 응답으로 거부한다.")
  @Test
  void attachPhoto_multipartLimitExceeded_returnsPayloadTooLarge() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "large.png", "image/png", new byte[] {1});
    given(visitService.attachPhoto(9001L, file, 9L))
        .willThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));

    mockMvc
        .perform(
            multipart("/api/v1/visits/{visitId}/photos", 9001L)
                .file(file)
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("VISIT413"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @DisplayName("지원하지 않는 사진 형식은 415로 거부한다.")
  @Test
  void attachPhoto_unsupportedImage_returnsUnsupportedMediaType() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "visit.gif", "image/gif", new byte[] {1});
    given(visitService.attachPhoto(9001L, file, 9L))
        .willThrow(new VisitHandler(ErrorStatus.VISIT_PHOTO_UNSUPPORTED_TYPE));

    mockMvc
        .perform(
            multipart("/api/v1/visits/{visitId}/photos", 9001L)
                .file(file)
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("VISIT415"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @DisplayName("인증 없이 방문 사진을 첨부하면 거부한다.")
  @Test
  void attachPhoto_unauthenticated_returnsUnauthorized() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "visit.png", "image/png", new byte[] {1});

    mockMvc
        .perform(multipart("/api/v1/visits/{visitId}/photos", 9001L).file(file))
        .andExpect(status().isUnauthorized());

    then(visitService).shouldHaveNoInteractions();
  }

  @DisplayName("GPS 정확도가 50m를 초과하면 방문 인증을 거부한다.")
  @Test
  void confirmVisit_inaccurateLocation_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/visits")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "explorationId": 44,
                      "placeId": 121,
                      "latitude": 35.1402,
                      "longitude": 126.9124,
                      "accuracyMeters": 50.1
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"))
        .andExpect(jsonPath("$.success").value(false));

    then(visitService).shouldHaveNoInteractions();
  }

  @DisplayName("위도 범위를 벗어난 좌표면 방문 인증을 거부한다.")
  @Test
  void confirmVisit_invalidLatitude_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/visits")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "explorationId": 44,
                      "placeId": 121,
                      "latitude": 91,
                      "longitude": 126.9124,
                      "accuracyMeters": 18.5
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"));

    then(visitService).shouldHaveNoInteractions();
  }

  @DisplayName("인증 없이 방문 인증을 요청하면 거부한다.")
  @Test
  void confirmVisit_unauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/visits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "explorationId": 44,
                      "placeId": 121,
                      "latitude": 35.1402,
                      "longitude": 126.9124,
                      "accuracyMeters": 18.5
                    }
                    """))
        .andExpect(status().isUnauthorized());

    then(visitService).shouldHaveNoInteractions();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, VisitController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    VisitService visitService() {
      return org.mockito.Mockito.mock(VisitService.class);
    }
  }
}
