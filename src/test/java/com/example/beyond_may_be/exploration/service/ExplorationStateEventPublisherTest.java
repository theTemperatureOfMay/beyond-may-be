package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
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
class ExplorationStateEventPublisherTest {

  private static final Map<String, Object> JSON_HEADERS =
      Map.of(MessageHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

  @InjectMocks private ExplorationStateEventPublisher eventPublisher;

  @Mock private SimpMessagingTemplate messagingTemplate;

  @DisplayName("신규 참여자 합류 이벤트를 커밋 후 상태 이벤트 채널로 전파한다.")
  @Test
  void publish_participantJoinedEvent_sendsAfterCommit() throws NoSuchMethodException {
    ExplorationDtos.ParticipantJoinedEvent event = participantJoinedEvent();

    eventPublisher.publish(event);

    then(messagingTemplate)
        .should()
        .convertAndSend("/topic/explorations/44/events", event, JSON_HEADERS);
    Method publishMethod =
        ExplorationStateEventPublisher.class.getMethod(
            "publish", ExplorationDtos.ParticipantJoinedEvent.class);
    TransactionalEventListener listener =
        publishMethod.getAnnotation(TransactionalEventListener.class);
    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  @DisplayName("탐험 시작 이벤트를 커밋 후 상태 이벤트 채널로 전파한다.")
  @Test
  void publish_startedEvent_sendsAfterCommit() throws NoSuchMethodException {
    ExplorationDtos.ExplorationStartedEvent event = event();

    eventPublisher.publish(event);

    then(messagingTemplate)
        .should()
        .convertAndSend("/topic/explorations/44/events", event, JSON_HEADERS);
    Method publishMethod =
        ExplorationStateEventPublisher.class.getMethod(
            "publish", ExplorationDtos.ExplorationStartedEvent.class);
    TransactionalEventListener listener =
        publishMethod.getAnnotation(TransactionalEventListener.class);
    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  @DisplayName("커밋 후 이벤트 전파 실패는 이미 성공한 상태 전환을 뒤집지 않는다.")
  @Test
  void publish_deliveryFailure_doesNotPropagate() {
    ExplorationDtos.ExplorationStartedEvent event = event();
    willThrow(new IllegalStateException("broker unavailable"))
        .given(messagingTemplate)
        .convertAndSend("/topic/explorations/44/events", event, JSON_HEADERS);

    assertThatCode(() -> eventPublisher.publish(event)).doesNotThrowAnyException();
  }

  @DisplayName("위치 공유 변경 이벤트를 커밋 후 상태 이벤트 채널로 전파한다.")
  @Test
  void publish_locationSharingChangedEvent_sendsAfterCommit() throws NoSuchMethodException {
    ExplorationDtos.LocationSharingChangedEvent event = locationSharingChangedEvent();

    eventPublisher.publish(event);

    then(messagingTemplate)
        .should()
        .convertAndSend("/topic/explorations/44/events", event, JSON_HEADERS);
    Method publishMethod =
        ExplorationStateEventPublisher.class.getMethod(
            "publish", ExplorationDtos.LocationSharingChangedEvent.class);
    TransactionalEventListener listener =
        publishMethod.getAnnotation(TransactionalEventListener.class);
    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  @DisplayName("위치 공유 변경 이벤트 전파 실패는 성공한 설정 변경을 뒤집지 않는다.")
  @Test
  void publish_locationSharingDeliveryFailure_doesNotPropagate() {
    ExplorationDtos.LocationSharingChangedEvent event = locationSharingChangedEvent();
    willThrow(new IllegalStateException("broker unavailable"))
        .given(messagingTemplate)
        .convertAndSend("/topic/explorations/44/events", event, JSON_HEADERS);

    assertThatCode(() -> eventPublisher.publish(event)).doesNotThrowAnyException();
  }

  @DisplayName("탐험 완료 이벤트를 커밋 후 상태 이벤트 채널로 전파한다.")
  @Test
  void publish_explorationCompletedEvent_sendsAfterCommit() throws NoSuchMethodException {
    ExplorationDtos.ExplorationCompletedEvent event = explorationCompletedEvent();

    eventPublisher.publish(event);

    then(messagingTemplate)
        .should()
        .convertAndSend("/topic/explorations/44/events", event, JSON_HEADERS);
    Method publishMethod =
        ExplorationStateEventPublisher.class.getMethod(
            "publish", ExplorationDtos.ExplorationCompletedEvent.class);
    TransactionalEventListener listener =
        publishMethod.getAnnotation(TransactionalEventListener.class);
    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  private ExplorationDtos.ExplorationStartedEvent event() {
    OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-15T10:00:00+09:00");
    return new ExplorationDtos.ExplorationStartedEvent(
        UUID.fromString("5bc86a37-5608-4eb1-8bea-8d38d85d5887"),
        "EXPLORATION_STARTED",
        44L,
        startedAt,
        new ExplorationDtos.ExplorationStartedData("ONGOING", 72L, startedAt));
  }

  private ExplorationDtos.ParticipantJoinedEvent participantJoinedEvent() {
    return new ExplorationDtos.ParticipantJoinedEvent(
        UUID.fromString("d8ed786d-7fce-4bb8-b174-3256a573603c"),
        "PARTICIPANT_JOINED",
        44L,
        OffsetDateTime.parse("2026-08-15T21:15:00+09:00"),
        new ExplorationDtos.ParticipantJoinedData(72L, "김감자감자 (2)", "MEMBER", 4));
  }

  private ExplorationDtos.LocationSharingChangedEvent locationSharingChangedEvent() {
    return new ExplorationDtos.LocationSharingChangedEvent(
        UUID.fromString("bc54fae8-c321-4e87-8573-d4e9a205a8bd"),
        "LOCATION_SHARING_CHANGED",
        44L,
        OffsetDateTime.parse("2026-08-15T21:20:00+09:00"),
        new ExplorationDtos.LocationSharingChangedData(72L, true));
  }

  private ExplorationDtos.ExplorationCompletedEvent explorationCompletedEvent() {
    OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-15T18:20:00+09:00");
    return new ExplorationDtos.ExplorationCompletedEvent(
        UUID.fromString("fb7069a5-a226-4100-833e-95c9f69b4e9c"),
        "EXPLORATION_COMPLETED",
        44L,
        completedAt,
        new ExplorationDtos.ExplorationCompletedData(
            "COMPLETED", completedAt, "OWNER_EARLY_COMPLETION"));
  }
}
