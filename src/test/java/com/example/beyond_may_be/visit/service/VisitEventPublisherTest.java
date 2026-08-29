package com.example.beyond_may_be.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class VisitEventPublisherTest {

  @InjectMocks private VisitEventPublisher eventPublisher;

  @Mock private SimpMessagingTemplate messagingTemplate;

  @DisplayName("방문 인증 이벤트를 커밋 후 JSON으로 방문 채널에 전파한다.")
  @Test
  void publish_visitConfirmed_sendsAfterCommitAsJson() throws NoSuchMethodException {
    VisitDtos.VisitConfirmedEvent event = visitConfirmedEvent();

    eventPublisher.publish(event);

    then(messagingTemplate)
        .should()
        .convertAndSend(
            "/topic/explorations/44/visits",
            event,
            Map.of(MessageHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    Method method =
        VisitEventPublisher.class.getMethod("publish", VisitDtos.VisitConfirmedEvent.class);
    TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  @DisplayName("WebSocket 전파 실패는 커밋된 방문 인증 결과에 영향을 주지 않는다.")
  @Test
  void publish_deliveryFails_doesNotPropagateException() {
    VisitDtos.VisitConfirmedEvent event = visitConfirmedEvent();
    doThrow(new IllegalStateException("broker unavailable"))
        .when(messagingTemplate)
        .convertAndSend(
            "/topic/explorations/44/visits",
            event,
            Map.of(MessageHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));

    assertThatCode(() -> eventPublisher.publish(event)).doesNotThrowAnyException();
  }

  private VisitDtos.VisitConfirmedEvent visitConfirmedEvent() {
    OffsetDateTime visitedAt = OffsetDateTime.parse("2026-08-15T14:32:10+09:00");
    return new VisitDtos.VisitConfirmedEvent(
        UUID.fromString("839dc55c-228b-4d1a-b836-04286209c98f"),
        "VISIT_CONFIRMED",
        44L,
        visitedAt,
        new VisitDtos.VisitConfirmedData(
            9001L,
            72L,
            "김감자감자",
            121L,
            null,
            visitedAt,
            true,
            new ExplorationDtos.CourseProgressResponse(2L, 5L, 40),
            "ONGOING"));
  }
}
