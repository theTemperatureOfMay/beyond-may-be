package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.apiPayload.exception.ExceptionAdvice;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.config.SecurityConfig;
import com.example.beyond_may_be.exploration.service.NearbyPlaceService;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = NearbyPlaceControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class NearbyPlaceControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private NearbyPlaceService nearbyPlaceService;

  @BeforeEach
  void authenticate() {
    reset(authTokenService, nearbyPlaceService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(2L));
  }

  @Test
  void returnsNearbyPlacesForExploration() throws Exception {
    given(
            nearbyPlaceService.getNearbyPlaces(
                44L, 2L, new BigDecimal("35.146600"), new BigDecimal("126.919900")))
        .willReturn(
            new PlaceDtos.NearbyPlacesResponse(
                List.of(
                    new PlaceDtos.NearbyPlaceResponse(
                        101L,
                        "국립아시아문화전당",
                        "박물관",
                        new BigDecimal("35.146702"),
                        new BigDecimal("126.919998"),
                        15L,
                        "https://example.test/place.jpg"))));

    mockMvc
        .perform(
            get("/api/v1/explorations/44/nearby-places")
                .queryParam("latitude", "35.146600")
                .queryParam("longitude", "126.919900")
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMMON200"))
        .andExpect(jsonPath("$.data.places[0].placeId").value(101))
        .andExpect(jsonPath("$.data.places[0].name").value("국립아시아문화전당"))
        .andExpect(jsonPath("$.data.places[0].category").value("박물관"))
        .andExpect(jsonPath("$.data.places[0].latitude").value(35.146702))
        .andExpect(jsonPath("$.data.places[0].longitude").value(126.919998))
        .andExpect(jsonPath("$.data.places[0].distanceMeters").value(15))
        .andExpect(
            jsonPath("$.data.places[0].thumbnailUrl").value("https://example.test/place.jpg"));
  }

  @ParameterizedTest
  @CsvSource({"91, 126.919900", "35.146600, 181"})
  void rejectsCoordinatesOutsideGlobalRange(String latitude, String longitude) throws Exception {
    mockMvc
        .perform(
            get("/api/v1/explorations/44/nearby-places")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .header("Authorization", "Bearer valid-token"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON400"));
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/explorations/44/nearby-places")
                .queryParam("latitude", "35.146600")
                .queryParam("longitude", "126.919900"))
        .andExpect(status().isUnauthorized());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, NearbyPlaceController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    NearbyPlaceService nearbyPlaceService() {
      return org.mockito.Mockito.mock(NearbyPlaceService.class);
    }
  }
}
