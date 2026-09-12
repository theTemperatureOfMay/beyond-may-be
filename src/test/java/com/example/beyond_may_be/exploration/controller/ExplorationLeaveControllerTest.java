package com.example.beyond_may_be.exploration.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExplorationCompleteControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class ExplorationLeaveControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private AuthTokenService auth;
  @Autowired private ExplorationService service;

  @BeforeEach
  void setUp() {
    reset(auth, service);
    given(auth.resolveUserId("valid-token")).willReturn(Optional.of(9L));
  }

  @Test
  void leaveReturnsCurrentUserParticipationAndSuccessor() throws Exception {
    given(service.leave(44L, 9L))
        .willReturn(
            new ExplorationDtos.LeaveResponse(
                44L, 72L, "LEFT", OffsetDateTime.parse("2026-09-12T12:00:00+09:00"), 73L));
    mockMvc
        .perform(
            post("/api/v1/explorations/44/leave").header("Authorization", "Bearer valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("LEFT"))
        .andExpect(jsonPath("$.data.participantId").value(72))
        .andExpect(jsonPath("$.data.ownerParticipantId").value(73));
  }

  @Test
  void leaveRequiresAuthentication() throws Exception {
    mockMvc.perform(post("/api/v1/explorations/44/leave")).andExpect(status().isUnauthorized());
  }

  @Test
  void openApiDocumentsLeave() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/explorations/{explorationId}/leave'].post.responses['200']")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/explorations/{explorationId}/leave'].post.responses['409']")
                .exists());
  }
}
