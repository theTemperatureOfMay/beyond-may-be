package com.example.beyond_may_be.exploration.converter;

import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import java.util.List;
import java.util.Map;

public final class ExplorationConverter {
  private ExplorationConverter() {}

  public static ExplorationDtos.JoinResponse toJoinResponse(ExplorationParticipant participant) {
    return new ExplorationDtos.JoinResponse(
        participant.getExplorationId(),
        participant.getId(),
        participant.getDisplayName(),
        participant.getRole().name());
  }

  public static ExplorationDtos.MemberListResponse toMemberListResponse(
      List<ExplorationParticipant> participants, Map<Long, Integer> visitedCountsByParticipantId) {
    List<ExplorationDtos.MemberResponse> members =
        participants.stream()
            .map(
                participant ->
                    new ExplorationDtos.MemberResponse(
                        participant.getUserId(),
                        participant.getDisplayName(),
                        visitedCountsByParticipantId.getOrDefault(participant.getId(), 0)))
            .toList();
    return new ExplorationDtos.MemberListResponse(members);
  }

  public static ExplorationDtos.StartResponse toStartResponse(Exploration exploration) {
    return new ExplorationDtos.StartResponse(
        exploration.getId(), exploration.getStatus().name(), exploration.getStartedAt());
  }
}
