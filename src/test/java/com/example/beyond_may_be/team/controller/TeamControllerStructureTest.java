package com.example.beyond_may_be.team.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class TeamControllerStructureTest {
  @Test
  void declaresTeamEndpoint() {
    assertEndpoint(
        "com.example.beyond_may_be.team.controller.TeamController",
        "setMaxMembers",
        PostMapping.class,
        "/api/teams/{teamId}/max-members");
  }
}
