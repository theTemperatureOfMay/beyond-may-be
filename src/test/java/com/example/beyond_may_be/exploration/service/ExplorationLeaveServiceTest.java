package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExplorationLeaveServiceTest {
  @InjectMocks private ExplorationService service;
  @Mock private ExplorationRepository explorations;
  @Mock private ExplorationParticipantRepository participants;
  @Mock private UserRepository users;
  @Mock private VisitRepository visits;
  @Mock private ApplicationEventPublisher events;

  @Test
  void repeatedLeavePreservesTimeAndDoesNotPublishAgain() {
    var owner = participant(1L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    var leftAt = LocalDateTime.of(2026, 9, 12, 12, 0);
    owner.leave(leftAt);
    prepare(List.of(owner));
    var response = service.leave(44L, 1L);
    assertThat(owner.getLeftAt()).isEqualTo(leftAt);
    assertThat(response.ownerParticipantId()).isNull();
    verifyNoInteractions(events, visits);
  }

  @Test
  void lastOwnerCanLeaveWithoutDeletingExploration() {
    var owner = participant(1L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    prepare(List.of(owner));
    assertThat(service.leave(44L, 1L).ownerParticipantId()).isNull();
    assertThat(owner.getRole()).isEqualTo(ParticipantRole.OWNER);
    assertThat(owner.getStatus()).isEqualTo(ParticipantStatus.LEFT);
    verifyNoInteractions(visits);
  }

  @Test
  void memberLeaveDoesNotChangeOwner() {
    var member = participant(1L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);
    var owner = participant(2L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    prepare(List.of(member, owner));
    assertThat(service.leave(44L, 1L).ownerParticipantId()).isEqualTo(2L);
    assertThat(owner.getRole()).isEqualTo(ParticipantRole.OWNER);
    assertThat(owner.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
  }

  @Test
  void nonParticipantCannotLeave() {
    prepare(List.of(participant(2L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE)));
    assertThatThrownBy(() -> service.leave(44L, 1L)).isInstanceOf(ExplorationHandler.class);
    verifyNoInteractions(events, visits);
  }

  @Test
  void completedExplorationCannotBeLeft() {
    var owner = participant(1L, ParticipantRole.OWNER, ParticipantStatus.COMPLETED);
    prepare(List.of(owner));
    given(explorations.findByIdForUpdate(44L))
        .willReturn(
            Optional.of(
                Exploration.builder().courseId(10L).status(ExplorationStatus.COMPLETED).build()));
    assertThatThrownBy(() -> service.leave(44L, 1L)).isInstanceOf(ExplorationHandler.class);
    assertThat(owner.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
    verifyNoInteractions(events, visits);
  }

  @Test
  void ownerLeavesAndTransfersToEarliestActiveMemberWithoutDeletingRecords() {
    var owner = participant(1L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    var earliest = participant(2L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);
    var later = participant(3L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);
    prepare(List.of(later, owner, earliest));

    var response = service.leave(44L, 1L);

    assertThat(owner.getStatus()).isEqualTo(ParticipantStatus.LEFT);
    assertThat(owner.isLocationSharingEnabled()).isFalse();
    assertThat(owner.getLeftAt()).isNotNull();
    assertThat(owner.getRole()).isEqualTo(ParticipantRole.MEMBER);
    assertThat(earliest.getRole()).isEqualTo(ParticipantRole.OWNER);
    assertThat(later.getRole()).isEqualTo(ParticipantRole.MEMBER);
    assertThat(response.ownerParticipantId()).isEqualTo(2L);
    assertThat(response.status()).isEqualTo("LEFT");
    verifyNoInteractions(visits);
  }

  private void prepare(List<ExplorationParticipant> team) {
    var exploration = Exploration.builder().courseId(10L).status(ExplorationStatus.ONGOING).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    given(explorations.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(users.findByIdForUpdate(1L))
        .willReturn(Optional.of(User.builder().nickname("여행자").build()));
    given(participants.findByExplorationIdForUpdate(44L)).willReturn(team);
  }

  private ExplorationParticipant participant(
      long id, ParticipantRole role, ParticipantStatus status) {
    var participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(id)
            .role(role)
            .status(status)
            .displayName("여행자 " + id)
            .locationSharingEnabled(true)
            .joinedAt(LocalDateTime.of(2026, 9, 12, 10, 0).plusMinutes(id))
            .build();
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }
}
