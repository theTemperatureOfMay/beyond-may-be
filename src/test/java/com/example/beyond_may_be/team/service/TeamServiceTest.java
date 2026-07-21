package com.example.beyond_may_be.team.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersRequest;
import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersResponse;
import org.junit.jupiter.api.Test;

class TeamServiceTest {

  @Test
  void returnsMaximumMemberSetting() {
    TeamService service = new TeamService();

    MaxMembersResponse response = service.setMaxMembers(1L, new MaxMembersRequest(5));

    assertEquals("1", response.teamId());
    assertEquals(5, response.maxMembers());
  }
}
