package com.example.beyond_may_be.exploration.dto;

public final class ExplorationDtos {
  private ExplorationDtos() {}

  public record JoinResponse(
      Long explorationId, Long participantId, String displayName, String role) {}
}
