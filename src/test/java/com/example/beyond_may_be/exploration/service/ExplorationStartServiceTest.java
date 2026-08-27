package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.course.repository.CourseRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.user.repository.UserRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
class ExplorationStartServiceTest {

  @InjectMocks private ExplorationService explorationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private UserRepository userRepository;
  @Mock private VisitRepository visitRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @DisplayName("활성 참여자가 시작 전 탐험을 시작한다.")
  @Test
  void start_activeParticipant_startsBeforeExploration() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.BEFORE).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(9L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("별밤지기")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.of(2026, 8, 14, 18, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", 72L);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(explorationRepository.startIfBefore(eq(44L), eq(72L), any(LocalDateTime.class)))
        .willReturn(1);

    ExplorationDtos.StartResponse response = explorationService.start(44L, 9L);

    ArgumentCaptor<LocalDateTime> startedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    then(explorationRepository).should().startIfBefore(eq(44L), eq(72L), startedAtCaptor.capture());
    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.courseId()).isEqualTo(31L);
    assertThat(response.status()).isEqualTo("ONGOING");
    assertThat(response.participantId()).isEqualTo(72L);
    assertThat(response.startedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(response.startedAt().toInstant())
        .isEqualTo(startedAtCaptor.getValue().atZone(ZoneId.systemDefault()).toInstant());
  }

  @DisplayName("존재하지 않는 탐험은 시작할 수 없다.")
  @Test
  void start_missingExploration_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.start(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
  }

  @DisplayName("활성 상태가 아닌 참여자는 탐험을 시작할 수 없다.")
  @Test
  void start_inactiveParticipant_throwsForbidden() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.BEFORE).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    ExplorationParticipant leftParticipant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(9L)
            .role(ParticipantRole.OWNER)
            .status(ParticipantStatus.LEFT)
            .displayName("별밤지기")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.of(2026, 8, 14, 18, 0))
            .build();
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(leftParticipant));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.start(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
    then(explorationRepository)
        .should(never())
        .startIfBefore(anyLong(), anyLong(), any(LocalDateTime.class));
  }

  @DisplayName("다른 요청이 먼저 탐험을 시작하면 충돌을 반환한다.")
  @Test
  void start_lostConditionalUpdate_throwsConflict() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.BEFORE).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(9L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("별밤지기")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.of(2026, 8, 14, 18, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", 72L);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(explorationRepository.startIfBefore(eq(44L), eq(72L), any(LocalDateTime.class)))
        .willReturn(0);

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.start(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_START_CONFLICT);
  }
}
