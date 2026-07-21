package com.example.beyond_may_be.common.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SecurityPolicyIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class SecurityPolicyIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void publicEndpointsAreAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void unlistedEndpointsAreForbidden() throws Exception {
    mockMvc.perform(get("/api/not-allowed")).andExpect(status().isForbidden());
    mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
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
  static class TestApplication {}
}
