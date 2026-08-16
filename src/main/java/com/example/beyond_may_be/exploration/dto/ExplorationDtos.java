package com.example.beyond_may_be.exploration.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class ExplorationDtos {
  private ExplorationDtos() {}

  public record JoinResponse(
      Long explorationId, Long participantId, String displayName, String role) {}

  public record MemberResponse(Long userId, String displayName, int visitedCount) {}

  public record MemberListResponse(List<MemberResponse> members) {}

  public record StartResponse(Long explorationId, String status, LocalDateTime startedAt) {}
}
