package com.example.beyond_may_be.route.controller;

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
import com.example.beyond_may_be.route.dto.RouteDtos;
import com.example.beyond_may_be.route.service.RouteService;
import java.math.BigDecimal;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = RouteControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class RouteControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private RouteService routeService;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @BeforeEach
  void authenticate() {
    reset(authTokenService, routeService);
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(1L));
  }

  @Test
  void returnsWalkingAndPublicTransitRoutes() throws Exception {
    given(
            routeService.getRoute(
                new BigDecimal("126.900000"),
                new BigDecimal("35.100000"),
                new BigDecimal("126.910000"),
                new BigDecimal("35.110000")))
        .willReturn(
            new RouteDtos.RouteResult(
                new RouteDtos.RouteResponse(
                    jsonMapper.readTree("{\"id\":\"walk-1\"}"),
                    jsonMapper.readTree("{\"id\":\"bus-1\"}")),
                false));

    mockMvc
        .perform(
            get("/api/v1/routes")
                .header("Authorization", "Bearer valid-token")
                .param("startLng", "126.900000")
                .param("startLat", "35.100000")
                .param("endLng", "126.910000")
                .param("endLat", "35.110000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.walking.id").value("walk-1"))
        .andExpect(jsonPath("$.data.publicTransit.id").value("bus-1"));
  }

  @Test
  void returnsPartialRouteWarningWithOkStatus() throws Exception {
    given(
            routeService.getRoute(
                new BigDecimal("126.900000"),
                new BigDecimal("35.100000"),
                new BigDecimal("126.910000"),
                new BigDecimal("35.110000")))
        .willReturn(
            new RouteDtos.RouteResult(
                new RouteDtos.RouteResponse(
                    jsonMapper.readTree("{\"id\":\"walk-1\"}"),
                    jsonMapper.readTree("{\"id\":\"bus-1\"}")),
                true));

    mockMvc
        .perform(
            get("/api/v1/routes")
                .header("Authorization", "Bearer valid-token")
                .param("startLng", "126.900000")
                .param("startLat", "35.100000")
                .param("endLng", "126.910000")
                .param("endLat", "35.110000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ROUTE200_1"))
        .andExpect(jsonPath("$.message").value("일부 도보 구간을 조회하지 못해 대중교통 경로만 반환합니다."));
  }

  @Test
  void reportsNoRouteAsNotFound() throws Exception {
    given(
            routeService.getRoute(
                new BigDecimal("126.900000"),
                new BigDecimal("35.100000"),
                new BigDecimal("126.910000"),
                new BigDecimal("35.110000")))
        .willThrow(new GeneralException(ErrorStatus.ROUTE_NOT_FOUND));

    mockMvc
        .perform(
            get("/api/v1/routes")
                .header("Authorization", "Bearer valid-token")
                .param("startLng", "126.900000")
                .param("startLat", "35.100000")
                .param("endLng", "126.910000")
                .param("endLat", "35.110000"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUTE404"));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, RouteController.class, ExceptionAdvice.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return org.mockito.Mockito.mock(AuthTokenService.class);
    }

    @Bean
    RouteService routeService() {
      return org.mockito.Mockito.mock(RouteService.class);
    }
  }
}
