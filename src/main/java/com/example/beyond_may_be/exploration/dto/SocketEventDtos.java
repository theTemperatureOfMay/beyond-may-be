package com.example.beyond_may_be.exploration.dto;

import java.math.BigDecimal;
import java.util.List;

public final class SocketEventDtos {
  private SocketEventDtos() {}

  // Client -> Server
  public record ExplorationJoinPayload(Long explorationId) {}

  public record ExplorationLeavePayload(Long explorationId) {}

  public record LocationUpdatePayload(
      Long explorationId, BigDecimal latitude, BigDecimal longitude) {}

  public record LocationOptInPayload(Long explorationId, boolean enabled) {}

  // Server -> Client
  public record VisitConfirmedPayload(
      Long placeId, Long userId, String displayName, long visitedAt) {}

  public record MemberProgressPayload(Long userId, String displayName, int visitedCount) {}

  public record MemberLocationPayload(Long userId, BigDecimal latitude, BigDecimal longitude) {}

  public record MemberPresencePayload(Long userId, String displayName) {}

  public record ExplorationStatePayload(
      List<Long> visitedPlaceIds, List<MemberProgressPayload> members) {}
}
