package com.example.beyond_may_be.exploration.service;

import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExplorationStateEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.ExplorationStartedEvent event) {
    try {
      messagingTemplate.convertAndSend(
          "/topic/explorations/" + event.explorationId() + "/events", event);
    } catch (RuntimeException exception) {
      log.error("탐험 시작 상태 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(ExplorationDtos.LocationSharingChangedEvent event) {
    try {
      messagingTemplate.convertAndSend(
          "/topic/explorations/" + event.explorationId() + "/events", event);
    } catch (RuntimeException exception) {
      log.warn("위치 공유 변경 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }
}
