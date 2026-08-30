package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.example.beyond_may_be.common.websocket.StompContractException;
import com.example.beyond_may_be.common.websocket.StompContractException.Code;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@ExtendWith(MockitoExtension.class)
class ExplorationLocationServiceTest {

  @InjectMocks private ExplorationLocationService locationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;

  @DisplayName("공유에 동의한 진행 중 탐험의 첫 위치를 위치 채널에 전파한다.")
  @Test
  void accept_firstValidLocation_publishesLocationUpdatedEvent() {
    ExplorationParticipant participant = participant(true);
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant));
    ExplorationDtos.LocationUpdateRequest request =
        new ExplorationDtos.LocationUpdateRequest(
            new BigDecimal("35.1402"),
            new BigDecimal("126.9124"),
            new BigDecimal("18.5"),
            OffsetDateTime.parse("2026-08-15T14:34:59+09:00"));

    locationService.accept("session-1", 44L, 8L, request);

    ArgumentCaptor<ExplorationDtos.LocationUpdatedEvent> eventCaptor =
        ArgumentCaptor.forClass(ExplorationDtos.LocationUpdatedEvent.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    then(messagingTemplate)
        .should()
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            eventCaptor.capture(),
            headersCaptor.capture());
    ExplorationDtos.LocationUpdatedEvent event = eventCaptor.getValue();
    assertThat(event.eventId()).isNotNull();
    assertThat(event.eventType()).isEqualTo("LOCATION_UPDATED");
    assertThat(event.explorationId()).isEqualTo(44L);
    assertThat(event.occurredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(event.data().participantId()).isEqualTo(72L);
    assertThat(event.data().displayName()).isEqualTo("김감자감자");
    assertThat(event.data().latitude()).isEqualByComparingTo("35.1402");
    assertThat(event.data().longitude()).isEqualByComparingTo("126.9124");
    assertThat(event.data().accuracyMeters()).isEqualByComparingTo("18.5");
    assertThat(event.data().recordedAt()).isEqualTo(request.recordedAt());
    assertThat(headersCaptor.getValue().get(MessageHeaders.CONTENT_TYPE))
        .isEqualTo(MimeTypeUtils.APPLICATION_JSON);
  }

  @DisplayName("위치 수락은 탐험과 참여자 상태를 같은 순서의 잠금으로 확인한다.")
  @Test
  void accept_locksExplorationThenParticipantBeforePublishing() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));

    locationService.accept("session-1", 44L, 8L, request("35.14020"));

    org.mockito.InOrder locks =
        org.mockito.Mockito.inOrder(explorationRepository, explorationParticipantRepository);
    locks.verify(explorationRepository).findByIdForUpdate(44L);
    locks.verify(explorationParticipantRepository).findByExplorationIdAndUserIdForUpdate(44L, 8L);
  }

  @DisplayName("같은 연결의 마지막 수락 위치에서 10m 미만 이동하면 새 이벤트를 전파하지 않는다.")
  @Test
  void accept_movedLessThanTenMeters_doesNotPublishAgain() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));

    locationService.accept("session-1", 44L, 8L, request("35.14020"));
    locationService.accept("session-1", 44L, 8L, request("35.14025"));

    then(messagingTemplate)
        .should(times(1))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("연결 종료 후 같은 위치를 보내면 새 연결의 첫 위치로 다시 전파한다.")
  @Test
  void disconnect_clearsLastAcceptedLocation() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));
    SessionDisconnectEvent event = org.mockito.Mockito.mock(SessionDisconnectEvent.class);
    given(event.getSessionId()).willReturn("session-1");

    locationService.accept("session-1", 44L, 8L, request("35.14020"));
    locationService.handleDisconnect(event);
    locationService.accept("session-1", 44L, 8L, request("35.14020"));

    then(messagingTemplate)
        .should(times(2))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("10m 미만 위치는 기준점을 바꾸지 않는다.")
  @Test
  void accept_movedLessThanTenMeters_keepsLastAcceptedLocation() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));

    locationService.accept("session-1", 44L, 8L, request("35.14020"));
    locationService.accept("session-1", 44L, 8L, request("35.14025"));
    locationService.accept("session-1", 44L, 8L, request("35.14030"));

    then(messagingTemplate)
        .should(times(2))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("서로 다른 연결은 같은 참여자라도 독립된 첫 위치를 전파한다.")
  @Test
  void accept_differentSessions_trackLocationsIndependently() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));

    locationService.accept("session-1", 44L, 8L, request("35.14020"));
    locationService.accept("session-2", 44L, 8L, request("35.14020"));

    then(messagingTemplate)
        .should(times(2))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("위치 공유 설정을 바꾼 뒤에는 같은 좌표도 새 기준 위치로 전파한다.")
  @Test
  void locationSharingChanged_clearsLastAcceptedLocation() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));
    ExplorationDtos.LocationSharingChangedEvent event =
        new ExplorationDtos.LocationSharingChangedEvent(
            UUID.randomUUID(),
            "LOCATION_SHARING_CHANGED",
            44L,
            OffsetDateTime.parse("2026-08-15T14:35:00+09:00"),
            new ExplorationDtos.LocationSharingChangedData(72L, false));

    locationService.accept("session-1", 44L, 8L, request("35.14020"));
    locationService.handleLocationSharingChanged(event);
    locationService.accept("session-1", 44L, 8L, request("35.14020"));

    then(messagingTemplate)
        .should(times(2))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("마지막 수락 위치에서 10m 이상 이동하면 새 위치를 전파한다.")
  @Test
  void accept_movedAtLeastTenMeters_publishesAgain() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));

    locationService.accept("session-1", 44L, 8L, request("35.14020"));
    locationService.accept("session-1", 44L, 8L, request("35.14030"));

    then(messagingTemplate)
        .should(times(2))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("유효한 대척점 좌표 이동도 10m 이상 이동으로 전파한다.")
  @Test
  void accept_antipodalMovement_publishesAgain() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));

    locationService.accept("session-1", 44L, 8L, request("-12", "-180"));
    locationService.accept("session-1", 44L, 8L, request("12", "0"));

    then(messagingTemplate)
        .should(times(2))
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/explorations/44/locations"),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdatedEvent.class),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @DisplayName("위치 공유에 동의하지 않은 참여자의 위치는 거부한다.")
  @Test
  void accept_locationSharingDisabled_isRejected() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(false)));

    assertThatThrownBy(() -> locationService.accept("session-1", 44L, 8L, request("35.14020")))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(Code.LOCATION_SHARING_DISABLED));
    then(messagingTemplate).shouldHaveNoInteractions();
  }

  @DisplayName("정확도가 50m를 초과한 위치는 거부한다.")
  @Test
  void accept_accuracyOverFiftyMeters_isRejected() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant(true)));
    ExplorationDtos.LocationUpdateRequest request =
        new ExplorationDtos.LocationUpdateRequest(
            new BigDecimal("35.1402"),
            new BigDecimal("126.9124"),
            new BigDecimal("50.1"),
            OffsetDateTime.parse("2026-08-15T14:34:59+09:00"));

    assertThatThrownBy(() -> locationService.accept("session-1", 44L, 8L, request))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(Code.LOCATION_ACCURACY_EXCEEDED));
    then(messagingTemplate).shouldHaveNoInteractions();
  }

  @DisplayName("진행 중이 아닌 탐험의 위치 채널은 구독할 수 없다.")
  @Test
  void validateSubscription_explorationIsNotOngoing_isRejected() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.BEFORE).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));

    assertThatThrownBy(() -> locationService.validateSubscription(44L, 8L))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(Code.EXPLORATION_NOT_ONGOING));
  }

  @DisplayName("존재하지 않는 탐험의 위치 채널은 구독할 수 없다.")
  @Test
  void validateSubscription_explorationDoesNotExist_isRejected() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> locationService.validateSubscription(44L, 8L))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(Code.EXPLORATION_NOT_ONGOING));
  }

  @DisplayName("활성 상태가 아닌 참여자는 위치 채널을 구독할 수 없다.")
  @Test
  void validateSubscription_participantIsNotActive_isRejected() {
    ExplorationParticipant participant = participant(true);
    ReflectionTestUtils.setField(participant, "status", ParticipantStatus.LEFT);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration()));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 8L))
        .willReturn(Optional.of(participant));

    assertThatThrownBy(() -> locationService.validateSubscription(44L, 8L))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(Code.PARTICIPANT_NOT_ACTIVE));
  }

  private Exploration exploration() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.ONGOING).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    return exploration;
  }

  private ExplorationParticipant participant(boolean sharingEnabled) {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(8L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("김감자감자")
            .locationSharingEnabled(sharingEnabled)
            .joinedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", 72L);
    return participant;
  }

  private ExplorationDtos.LocationUpdateRequest request(String latitude) {
    return request(latitude, "126.9124");
  }

  private ExplorationDtos.LocationUpdateRequest request(String latitude, String longitude) {
    return new ExplorationDtos.LocationUpdateRequest(
        new BigDecimal(latitude),
        new BigDecimal(longitude),
        new BigDecimal("18.5"),
        OffsetDateTime.parse("2026-08-15T14:34:59+09:00"));
  }
}
