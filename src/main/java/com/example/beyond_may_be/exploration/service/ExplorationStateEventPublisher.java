package com.example.beyond_may_be.exploration.service;

import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExplorationStateEventPublisher {

  private static final Map<String, Object> JSON_HEADERS =
      Map.of(MessageHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.ParticipantLeftEvent event) {
    try {
      send(event.explorationId(), event);
    } catch (RuntimeException exception) {
      log.warn("참여자 이탈 상태 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.ParticipantJoinedEvent event) {
    try {
      send(event.explorationId(), event);
    } catch (RuntimeException exception) {
      log.warn("참여자 합류 상태 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.ExplorationStartedEvent event) {
    try {
      send(event.explorationId(), event);
    } catch (RuntimeException exception) {
      log.warn("탐험 시작 상태 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.LocationSharingChangedEvent event) {
    try {
      send(event.explorationId(), event);
    } catch (RuntimeException exception) {
      log.warn("위치 공유 변경 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.ExplorationCompletedEvent event) {
    try {
      send(event.explorationId(), event);
    } catch (RuntimeException exception) {
      log.warn("탐험 완료 상태 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }

  private void send(Long explorationId, Object event) {
    messagingTemplate.convertAndSend(
        "/topic/explorations/" + explorationId + "/events", event, JSON_HEADERS);
  }
}
