package com.example.beyond_may_be.place.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.config.SecurityConfig;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.service.PlaceService;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
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

@SpringBootTest(classes = PlaceControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class PlaceControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private PlaceService placeService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, placeService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(1L));
  }

  @Test
  void returnsPlaceCatalogDetail() throws Exception {
    given(placeService.getDetail(101L))
        .willReturn(
            new PlaceDtos.DetailResponse(
                101L,
                "국립아시아문화전당",
                "전시",
                TravelPreferenceType.ARTIST,
                List.of("전시", "문화"),
                "광주광역시 동구 문화전당로 38",
                new BigDecimal("35.146600"),
                new BigDecimal("126.919900"),
                "평일 10:00-18:00",
                "검수된 상세 설명",
                "https://example.com/places/101.webp"));

    mockMvc
        .perform(get("/api/v1/places/101").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("성공입니다."))
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.placeId").value(101))
        .andExpect(jsonPath("$.data.name").value("국립아시아문화전당"))
        .andExpect(jsonPath("$.data.category").value("전시"))
        .andExpect(jsonPath("$.data.travelMbtiType").value("ARTIST"))
        .andExpect(jsonPath("$.data.tags[0]").value("전시"))
        .andExpect(jsonPath("$.data.address").value("광주광역시 동구 문화전당로 38"))
        .andExpect(jsonPath("$.data.latitude").value(35.1466))
        .andExpect(jsonPath("$.data.longitude").value(126.9199))
        .andExpect(jsonPath("$.data.businessHours").value("평일 10:00-18:00"))
        .andExpect(jsonPath("$.data.description").value("검수된 상세 설명"))
        .andExpect(jsonPath("$.data.thumbnailUrl").value("https://example.com/places/101.webp"));
  }

  @Test
  void reportsMissingOrInactivePlace() throws Exception {
    given(placeService.getDetail(404L))
        .willThrow(new GeneralException(ErrorStatus.PLACE_DETAIL_NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/places/404").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("장소를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.code").value("PLACE404"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void reportsTourApiFailureAsServiceUnavailable() throws Exception {
    given(placeService.getDetail(503L))
        .willThrow(new GeneralException(ErrorStatus.PLACE_DETAIL_UNAVAILABLE));

    mockMvc
        .perform(get("/api/v1/places/503").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("장소 상세정보를 일시적으로 불러오지 못했습니다."))
        .andExpect(jsonPath("$.code").value("PLACE503"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void reportsUnhandledFailureAsInternalServerError() throws Exception {
    given(placeService.getDetail(500L)).willThrow(new IllegalStateException("database failure"));

    mockMvc
        .perform(get("/api/v1/places/500").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("COMMON500"))
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc.perform(get("/api/v1/places/101")).andExpect(status().isUnauthorized());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, PlaceController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    PlaceService placeService() {
      return org.mockito.Mockito.mock(PlaceService.class);
    }
  }
}
