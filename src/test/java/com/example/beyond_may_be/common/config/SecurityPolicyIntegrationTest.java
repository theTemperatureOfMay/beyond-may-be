package com.example.beyond_may_be.common.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SecurityPolicyIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class SecurityPolicyIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private AuthTokenService authTokenService;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "http://localhost:3000, GET",
    "http://localhost:3000, POST",
    "http://localhost:3000, PUT",
    "http://localhost:3000, PATCH",
    "https://beyond-may.vercel.app, GET",
    "https://beyond-may.vercel.app, POST",
    "https://beyond-may.vercel.app, PUT",
    "https://beyond-may.vercel.app, PATCH"
  })
  void frontendPreflightAllowsBearerJsonRequests(String origin, String method) throws Exception {
    mockMvc
        .perform(
            options("/api/v1/users/login")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", origin))
        .andExpect(header().string("Access-Control-Allow-Headers", "authorization, content-type"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "http://localhost:3000, ''",
    "http://localhost:3000, Bearer valid-token",
    "https://beyond-may.vercel.app, ''",
    "https://beyond-may.vercel.app, Bearer valid-token"
  })
  void frontendCorsPreservesBearerAuthentication(String origin, String authorization)
      throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(1L));

    mockMvc
        .perform(
            get("/api/not-allowed").header("Origin", origin).header("Authorization", authorization))
        .andExpect(status().is(authorization.isEmpty() ? 401 : 404))
        .andExpect(header().string("Access-Control-Allow-Origin", origin))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }

  @Test
  void unlistedOriginCannotAccessRestApi() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/users/login")
                .header("Origin", "https://other.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mockMvc
        .perform(
            get("/api/v1/preference-tests/questions").header("Origin", "https://other.example"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void publicEndpointsAreAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void unlistedEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/not-allowed")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
  }

  @Test
  void validBearerTokenPassesAuthentication() throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(1L));

    // 인증은 통과하지만 해당 경로에 매핑된 컨트롤러가 없어 404를 반환한다(403이 아님).
    mockMvc
        .perform(get("/api/not-allowed").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void healthEndpointOnlyAllowsGet() throws Exception {
    mockMvc.perform(post("/actuator/health").with(csrf())).andExpect(status().isUnauthorized());
  }

  @Test
  void webSocketPermitOnlyCoversExactGetEndpoint() throws Exception {
    mockMvc.perform(post("/ws").with(csrf())).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/ws/anything")).andExpect(status().isUnauthorized());
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, SwaggerConfig.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return mock(AuthTokenService.class);
    }
  }
}
