package com.example.beyond_may_be.common.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

  @Test
  void publicEndpointsAreAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void unlistedEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/not-allowed")).andExpect(status().isForbidden());
    mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
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
    mockMvc.perform(post("/actuator/health").with(csrf())).andExpect(status().isForbidden());
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
