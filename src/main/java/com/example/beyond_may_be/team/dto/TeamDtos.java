package com.example.beyond_may_be.team.dto;

public final class TeamDtos {
  private TeamDtos() {}

  public record MaxMembersRequest(int maxMembers) {}

  public record MaxMembersResponse(String teamId, int maxMembers) {}
}
