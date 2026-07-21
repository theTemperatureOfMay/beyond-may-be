package com.example.beyond_may_be.team.converter;

import com.example.beyond_may_be.team.dto.TeamDtos.MaxMembersResponse;

public final class TeamConverter {
  private TeamConverter() {}

  public static MaxMembersResponse toMaxMembersResponse(Long teamId, int maxMembers) {
    return new MaxMembersResponse(String.valueOf(teamId), maxMembers);
  }
}
