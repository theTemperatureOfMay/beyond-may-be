package com.example.beyond_may_be.exploration.service;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.SocketEventDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExplorationBroadcastService {

  private static final String ROOM_PREFIX = "exploration:";

  private final SocketIOServer socketIOServer;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final VisitRepository visitRepository;

  public void broadcastVisitConfirmed(
      Long explorationId, Long placeId, Long userId, String displayName, LocalDateTime visitedAt) {
    socketIOServer
        .getRoomOperations(room(explorationId))
        .sendEvent(
            "visit:confirmed",
            new SocketEventDtos.VisitConfirmedPayload(
                placeId, userId, displayName, toEpochMilli(visitedAt)));
  }

  public void broadcastMemberProgress(
      Long explorationId, Long userId, String displayName, int visitedCount) {
    socketIOServer
        .getRoomOperations(room(explorationId))
        .sendEvent(
            "member:progress",
            new SocketEventDtos.MemberProgressPayload(userId, displayName, visitedCount));
  }

  public void broadcastMemberLocation(
      Long explorationId, Long userId, BigDecimal latitude, BigDecimal longitude) {
    socketIOServer
        .getRoomOperations(room(explorationId))
        .sendEvent(
            "member:location",
            new SocketEventDtos.MemberLocationPayload(userId, latitude, longitude));
  }

  public void broadcastMemberJoined(Long explorationId, Long userId, String displayName) {
    socketIOServer
        .getRoomOperations(room(explorationId))
        .sendEvent("member:joined", new SocketEventDtos.MemberPresencePayload(userId, displayName));
  }

  @Transactional(readOnly = true)
  public void sendStateSnapshot(SocketIOClient client, Long explorationId) {
    List<ExplorationParticipant> members =
        explorationParticipantRepository.findByExplorationIdAndStatus(
            explorationId, ParticipantStatus.ACTIVE);
    List<Long> participantIds = members.stream().map(ExplorationParticipant::getId).toList();

    Map<Long, Integer> visitedCountsByParticipantId = new HashMap<>();
    for (VisitRepository.ParticipantVisitCount count :
        visitRepository.countByParticipantIds(participantIds)) {
      visitedCountsByParticipantId.put(count.getParticipantId(), (int) count.getVisitCount());
    }

    List<SocketEventDtos.MemberProgressPayload> memberPayloads =
        members.stream()
            .map(
                participant ->
                    new SocketEventDtos.MemberProgressPayload(
                        participant.getUserId(),
                        participant.getDisplayName(),
                        visitedCountsByParticipantId.getOrDefault(participant.getId(), 0)))
            .toList();

    List<Long> visitedPlaceIds =
        visitRepository.findDistinctPlaceIdsByParticipantIds(participantIds);

    client.sendEvent(
        "exploration:state",
        new SocketEventDtos.ExplorationStatePayload(visitedPlaceIds, memberPayloads));
  }

  private String room(Long explorationId) {
    return ROOM_PREFIX + explorationId;
  }

  private long toEpochMilli(LocalDateTime dateTime) {
    return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }
}
