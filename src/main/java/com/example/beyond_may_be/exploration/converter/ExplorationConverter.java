package com.example.beyond_may_be.exploration.converter;

import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;

public final class ExplorationConverter {
  private ExplorationConverter() {}

  public static ExplorationDtos.JoinResponse toJoinResponse(ExplorationParticipant participant) {
    return new ExplorationDtos.JoinResponse(
        participant.getExplorationId(),
        participant.getId(),
        participant.getDisplayName(),
        participant.getRole().name());
  }
}
