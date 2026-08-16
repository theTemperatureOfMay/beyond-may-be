package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.repository.CourseRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExplorationServiceTest {

  @InjectMocks private ExplorationService explorationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private UserRepository userRepository;
  @Mock private com.example.beyond_may_be.visit.repository.VisitRepository visitRepository;

  @Mock private ExplorationBroadcastService explorationBroadcastService;

  private Course confirmedCourse(LocalDateTime shareExpiresAt) {
    Course course =
        Course.builder()
            .ownerUserId(1L)
            .title("광주 여행")
            .status(CourseStatus.DRAFT)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .startTime(LocalTime.of(9, 0))
            .aiRevisionCount(0)
            .build();
    course.confirm(LocalDateTime.now(), shareExpiresAt);
    return course;
  }

  private Exploration exploration(long id) {
    Exploration exploration =
        Exploration.builder().courseId(10L).status(ExplorationStatus.BEFORE).build();
    ReflectionTestUtils.setField(exploration, "id", id);
    return exploration;
  }

  @DisplayName("신규 사용자가 유효한 공유 링크로 합류하면 MEMBER Participant가 생성된다.")
  @Test
  void join_newUser_createsMemberParticipant() {
    // given
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseId(10L)).willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.existsActiveParticipationElsewhere(2L, 5L))
        .willReturn(false);
    given(explorationParticipantRepository.findByExplorationId(5L)).willReturn(List.of());
    given(userRepository.findById(2L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(2).build()));
    given(explorationParticipantRepository.save(any(ExplorationParticipant.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    ExplorationDtos.JoinResponse response = explorationService.join(10L, 2L);

    // then
    assertThat(response.displayName()).isEqualTo("여행자");
    ArgumentCaptor<ExplorationParticipant> captor =
        ArgumentCaptor.forClass(ExplorationParticipant.class);
    Mockito.verify(explorationParticipantRepository).save(captor.capture());
    assertThat(captor.getValue().getRole()).isEqualTo(ParticipantRole.MEMBER);
    assertThat(captor.getValue().getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
  }

  @DisplayName("같은 닉네임의 팀원이 이미 있으면 구분자를 붙인다.")
  @Test
  void join_duplicateNickname_appendsSuffix() {
    // given
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseId(10L)).willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 3L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.existsActiveParticipationElsewhere(3L, 5L))
        .willReturn(false);
    given(explorationParticipantRepository.findByExplorationId(5L))
        .willReturn(
            List.of(
                ExplorationParticipant.builder()
                    .explorationId(5L)
                    .userId(2L)
                    .role(ParticipantRole.MEMBER)
                    .status(ParticipantStatus.ACTIVE)
                    .displayName("여행자")
                    .locationSharingEnabled(false)
                    .joinedAt(LocalDateTime.now())
                    .build()));
    given(userRepository.findById(3L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(3).build()));
    given(explorationParticipantRepository.save(any(ExplorationParticipant.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    ExplorationDtos.JoinResponse response = explorationService.join(10L, 3L);

    // then
    assertThat(response.displayName()).isEqualTo("여행자 (2)");
  }

  @DisplayName("다른 탐험에 이미 활성 참여 중이면 합류가 차단된다.")
  @Test
  void join_duplicateActiveParticipation_throws() {
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseId(10L)).willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 3L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.existsActiveParticipationElsewhere(3L, 5L))
        .willReturn(true);

    assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 3L));
  }

  @DisplayName("만료된 공유 링크로 합류하면 예외가 발생한다.")
  @Test
  void join_expiredShareLink_throws() {
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().minusMinutes(1))));

    assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 2L));
  }

  @DisplayName("이미 참여했던 사용자가 다시 합류를 요청하면 기존 Participant를 재사용한다.")
  @Test
  void join_existingParticipant_reusesRecord() {
    ExplorationParticipant existing =
        ExplorationParticipant.builder()
            .explorationId(5L)
            .userId(2L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.LEFT)
            .displayName("여행자")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.now().minusDays(1))
            .leftAt(LocalDateTime.now())
            .build();
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseId(10L)).willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(existing));

    ExplorationDtos.JoinResponse response = explorationService.join(10L, 2L);

    assertThat(response.displayName()).isEqualTo("여행자");
    assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
    Mockito.verify(explorationParticipantRepository, Mockito.never())
        .save(any(ExplorationParticipant.class));
  }

  @DisplayName("활성 참여자만 팀원 목록을 조회할 수 있다.")
  @Test
  void listMembers_notActiveParticipant_throws() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 9L))
        .willReturn(Optional.empty());

    assertThrows(ExplorationHandler.class, () -> explorationService.listMembers(5L, 9L));
  }

  @DisplayName("최초 시작 요청만 BEFORE에서 ONGOING으로 전환된다.")
  @Test
  void start_firstRequestSucceeds() {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(5L)
            .userId(2L)
            .role(ParticipantRole.OWNER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("여행자")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.now())
            .build();
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(participant));
    given(explorationRepository.startIfBefore(anyLong(), any(), any())).willReturn(1);
    given(explorationRepository.findById(5L)).willReturn(Optional.of(exploration(5L)));

    ExplorationDtos.StartResponse response = explorationService.start(5L, 2L);

    assertThat(response.explorationId()).isEqualTo(5L);
  }

  @DisplayName("이미 시작된 탐험을 다시 시작하려 하면 예외가 발생한다.")
  @Test
  void start_alreadyStarted_throws() {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(5L)
            .userId(2L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("여행자")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.now())
            .build();
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(participant));
    given(explorationRepository.startIfBefore(anyLong(), any(), any())).willReturn(0);

    assertThrows(ExplorationHandler.class, () -> explorationService.start(5L, 2L));
  }
}
