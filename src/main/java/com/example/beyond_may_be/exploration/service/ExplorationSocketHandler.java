package com.example.beyond_may_be.exploration.service;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.beyond_may_be.exploration.dto.SocketEventDtos;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExplorationSocketHandler {

  private static final String ROOM_PREFIX = "exploration:";
  private static final String USER_ID_STORE_KEY = "userId";

  private final SocketIOServer socketIOServer;
  private final ExplorationBroadcastService explorationBroadcastService;
  private final ExplorationService explorationService;

  @PostConstruct
  public void registerListeners() {
    socketIOServer.addEventListener(
        "exploration:join", SocketEventDtos.ExplorationJoinPayload.class, this::onJoin);
    socketIOServer.addEventListener(
        "exploration:leave", SocketEventDtos.ExplorationLeavePayload.class, this::onLeave);
    socketIOServer.addEventListener(
        "location:update", SocketEventDtos.LocationUpdatePayload.class, this::onLocationUpdate);
    socketIOServer.addEventListener(
        "location:optIn", SocketEventDtos.LocationOptInPayload.class, this::onLocationOptIn);
  }

  private void onJoin(
      SocketIOClient client, SocketEventDtos.ExplorationJoinPayload payload, AckRequest ackRequest) {
    Long explorationId = payload.explorationId();
    client.joinRoom(room(explorationId));
    explorationBroadcastService.sendStateSnapshot(client, explorationId);
  }

  private void onLeave(
      SocketIOClient client, SocketEventDtos.ExplorationLeavePayload payload, AckRequest ackRequest) {
    client.leaveRoom(room(payload.explorationId()));
  }

  private void onLocationUpdate(
      SocketIOClient client, SocketEventDtos.LocationUpdatePayload payload, AckRequest ackRequest) {
    Long userId = resolveUserId(client);
    if (userId == null
        || !explorationService.isActiveAndLocationSharing(payload.explorationId(), userId)) {
      return;
    }
    explorationBroadcastService.broadcastMemberLocation(
        payload.explorationId(), userId, payload.latitude(), payload.longitude());
  }

  private void onLocationOptIn(
      SocketIOClient client, SocketEventDtos.LocationOptInPayload payload, AckRequest ackRequest) {
    Long userId = resolveUserId(client);
    if (userId == null) {
      return;
    }
    explorationService.setLocationSharing(payload.explorationId(), userId, payload.enabled());
  }

  private Long resolveUserId(SocketIOClient client) {
    return client.get(USER_ID_STORE_KEY);
  }

  private String room(Long explorationId) {
    return ROOM_PREFIX + explorationId;
  }
}
