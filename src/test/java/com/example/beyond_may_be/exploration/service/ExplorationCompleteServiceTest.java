package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExplorationCompleteServiceTest {

  @InjectMocks private ExplorationService explorationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private VisitRepository visitRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @DisplayName("ACTIVE OWNER가 진행 중 탐험을 조기 완료한다.")
  @Test
  void completeEarly_activeOwner_completesExplorationAndParticipants() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant owner =
        participant(70L, 9L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    ExplorationParticipant member =
        participant(71L, 10L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);
    ExplorationParticipant left =
        participant(72L, 11L, ParticipantRole.MEMBER, ParticipantStatus.LEFT);
    ExplorationParticipant completed =
        participant(73L, 12L, ParticipantRole.MEMBER, ParticipantStatus.COMPLETED);
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(owner));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(owner, member, left, completed));
    given(visitRepository.countDistinctCoursePlaceIds(anyList())).willReturn(3L);
    given(coursePlaceRepository.countByCourseId(31L)).willReturn(5L);

    ExplorationDtos.CompleteResponse response = explorationService.completeEarly(44L, 9L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.courseId()).isEqualTo(31L);
    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.completionReason()).isEqualTo("OWNER_EARLY_COMPLETION");
    assertThat(response.completedAt()).isNotNull();
    assertThat(response.completedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(response.courseProgress().completedCoursePlaceCount()).isEqualTo(3L);
    assertThat(response.courseProgress().totalCoursePlaceCount()).isEqualTo(5L);
    assertThat(response.courseProgress().completionRate()).isEqualTo(60);
    assertThat(exploration.getStatus()).isEqualTo(ExplorationStatus.COMPLETED);
    assertThat(owner.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
    assertThat(member.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
    assertThat(left.getStatus()).isEqualTo(ParticipantStatus.LEFT);
    assertThat(completed.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
    then(explorationRepository).should().findByIdForUpdate(44L);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue())
        .isInstanceOfSatisfying(
            ExplorationDtos.ExplorationCompletedEvent.class,
            event -> {
              assertThat(event.eventType()).isEqualTo("EXPLORATION_COMPLETED");
              assertThat(event.explorationId()).isEqualTo(44L);
              assertThat(event.occurredAt()).isEqualTo(response.completedAt());
              assertThat(event.data().status()).isEqualTo("COMPLETED");
              assertThat(event.data().completedAt()).isEqualTo(response.completedAt());
              assertThat(event.data().completionReason()).isEqualTo("OWNER_EARLY_COMPLETION");
            });
  }

  @DisplayName("존재하지 않는 탐험은 조기 완료할 수 없다.")
  @Test
  void completeEarly_missingExploration_throwsNotFound() {
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.completeEarly(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
  }

  @DisplayName("탐험 참여자가 아니면 조기 완료할 수 없다.")
  @Test
  void completeEarly_nonParticipant_throwsForbidden() {
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.empty());

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.completeEarly(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
  }

  @DisplayName("ACTIVE MEMBER는 탐험을 조기 완료할 수 없다.")
  @Test
  void completeEarly_activeMember_throwsForbidden() {
    ExplorationParticipant member =
        participant(70L, 9L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(member));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.completeEarly(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
  }

  @DisplayName("비활성 OWNER는 진행 중 탐험을 조기 완료할 수 없다.")
  @Test
  void completeEarly_inactiveOwner_throwsForbidden() {
    ExplorationParticipant owner =
        participant(70L, 9L, ParticipantRole.OWNER, ParticipantStatus.LEFT);
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(owner));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.completeEarly(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
  }

  @DisplayName("시작 전 탐험은 조기 완료할 수 없다.")
  @Test
  void completeEarly_beforeExploration_throwsConflict() {
    ExplorationParticipant owner =
        participant(70L, 9L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.BEFORE)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(owner));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.completeEarly(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_ONGOING);
  }

  @DisplayName("이미 완료된 탐험을 다시 조기 완료할 수 없다.")
  @Test
  void completeEarly_completedExploration_throwsConflict() {
    ExplorationParticipant owner =
        participant(70L, 9L, ParticipantRole.OWNER, ParticipantStatus.COMPLETED);
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.COMPLETED)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(owner));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.completeEarly(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
  }

  private Exploration exploration(ExplorationStatus status) {
    Exploration exploration = Exploration.builder().courseId(31L).status(status).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    return exploration;
  }

  private ExplorationParticipant participant(
      long id, long userId, ParticipantRole role, ParticipantStatus status) {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(userId)
            .role(role)
            .status(status)
            .displayName("여행자 " + id)
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }
}
