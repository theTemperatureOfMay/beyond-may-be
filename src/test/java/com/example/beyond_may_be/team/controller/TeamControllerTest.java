package com.example.beyond_may_be.team.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersRequest;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersResponse;
import com.example.beyond_may_be.team.service.TeamService;
import org.junit.jupiter.api.Test;

class TeamControllerTest {

  @Test
  void delegatesMaximumMemberSettingToService() {
    TeamService teamService = mock(TeamService.class);
    TeamController controller = new TeamController(teamService);
    MaxMembersRequest request = new MaxMembersRequest(5);
    MaxMembersResponse expectedResponse = new MaxMembersResponse("1", 5);

    when(teamService.setMaxMembers(1L, request)).thenReturn(expectedResponse);

    ApiResponse<MaxMembersResponse> response = controller.setMaxMembers(1L, request);

    assertEquals(expectedResponse, response.getData());
    verify(teamService).setMaxMembers(1L, request);
  }
}
