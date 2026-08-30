package com.example.beyond_may_be.visit.service;

import com.example.beyond_may_be.visit.dto.VisitDtos;
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
public class VisitEventPublisher {

  private static final Map<String, Object> JSON_HEADERS =
      Map.of(MessageHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(VisitDtos.VisitConfirmedEvent event) {
    try {
      messagingTemplate.convertAndSend(
          "/topic/explorations/" + event.explorationId() + "/visits", event, JSON_HEADERS);
    } catch (RuntimeException exception) {
      log.warn("방문 인증 이벤트 전파에 실패했습니다. explorationId={}", event.explorationId(), exception);
    }
  }
}
