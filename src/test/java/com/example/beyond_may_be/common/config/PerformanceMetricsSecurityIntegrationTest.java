package com.example.beyond_may_be.common.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = SecurityPolicyIntegrationTest.TestApplication.class,
    properties = "PERFORMANCE_TEST_ID=integration-test")
@AutoConfigureMockMvc
@ActiveProfiles("performance")
class PerformanceMetricsSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void performanceProfileExposesPrometheusMetricsWithTestId() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("testid=\"integration-test\"")));
  }
}
