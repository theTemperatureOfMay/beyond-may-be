package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
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
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExplorationServiceTest {

  @InjectMocks private ExplorationService explorationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private UserRepository userRepository;
  @Mock private VisitRepository visitRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

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

  private ExplorationParticipant participant(
      long id,
      long userId,
      ParticipantRole role,
      ParticipantStatus status,
      String displayName,
      boolean locationSharingEnabled,
      LocalDateTime joinedAt) {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(userId)
            .role(role)
            .status(status)
            .displayName(displayName)
            .locationSharingEnabled(locationSharingEnabled)
            .joinedAt(joinedAt)
            .build();
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }

  private VisitRepository.ParticipantVisitCount visitCount(long participantId, long visitCount) {
    return new VisitRepository.ParticipantVisitCount() {
      @Override
      public Long getParticipantId() {
        return participantId;
      }

      @Override
      public long getVisitCount() {
        return visitCount;
      }
    };
  }

  private OffsetDateTime toSeoulTime(LocalDateTime value) {
    return value
        .atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
        .toOffsetDateTime();
  }

  @DisplayName("신규 사용자가 유효한 공유 링크로 합류하면 MEMBER Participant가 생성된다.")
  @Test
  void join_newUser_createsMemberParticipant() {
    // given
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.findActiveExplorationId(2L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.findByExplorationIdForUpdate(5L))
        .willReturn(
            List.of(
                ExplorationParticipant.builder()
                    .explorationId(5L)
                    .userId(1L)
                    .role(ParticipantRole.OWNER)
                    .status(ParticipantStatus.ACTIVE)
                    .displayName("코스장")
                    .locationSharingEnabled(false)
                    .joinedAt(LocalDateTime.now().minusDays(1))
                    .build()));
    given(userRepository.findByIdForUpdate(2L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(2).build()));
    given(explorationParticipantRepository.save(any(ExplorationParticipant.class)))
        .willAnswer(
            invocation -> {
              ExplorationParticipant participant = invocation.getArgument(0);
              ReflectionTestUtils.setField(participant, "id", 72L);
              return participant;
            });

    // when
    ExplorationDtos.JoinResponse response = explorationService.join(10L, 2L);

    // then
    assertThat(response.displayName()).isEqualTo("여행자");
    assertThat(response.role()).isEqualTo("MEMBER");
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.locationSharingEnabled()).isFalse();
    assertThat(response.joinedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(response.alreadyJoined()).isFalse();
    ArgumentCaptor<ExplorationParticipant> captor =
        ArgumentCaptor.forClass(ExplorationParticipant.class);
    Mockito.verify(explorationParticipantRepository).save(captor.capture());
    assertThat(captor.getValue().getRole()).isEqualTo(ParticipantRole.MEMBER);
    assertThat(captor.getValue().getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
    ArgumentCaptor<ExplorationDtos.ParticipantJoinedEvent> eventCaptor =
        ArgumentCaptor.forClass(ExplorationDtos.ParticipantJoinedEvent.class);
    Mockito.verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    ExplorationDtos.ParticipantJoinedEvent event = eventCaptor.getValue();
    assertThat(event.eventId()).isNotNull();
    assertThat(event.eventType()).isEqualTo("PARTICIPANT_JOINED");
    assertThat(event.explorationId()).isEqualTo(5L);
    assertThat(event.occurredAt()).isEqualTo(response.joinedAt());
    assertThat(event.data().participantId()).isEqualTo(72L);
    assertThat(event.data().displayName()).isEqualTo("여행자");
    assertThat(event.data().role()).isEqualTo("MEMBER");
    assertThat(event.data().participantCount()).isEqualTo(2);
  }

  @DisplayName("같은 닉네임의 팀원이 이미 있으면 구분자를 붙인다.")
  @Test
  void join_duplicateNickname_appendsSuffix() {
    // given
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 3L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.findActiveExplorationId(3L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.findByExplorationIdForUpdate(5L))
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
    given(userRepository.findByIdForUpdate(3L))
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
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 3L))
        .willReturn(Optional.empty());
    given(userRepository.findByIdForUpdate(3L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(3).build()));
    given(explorationParticipantRepository.findActiveExplorationId(3L))
        .willReturn(Optional.of(44L));

    assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 3L));
  }

  @DisplayName("합류는 활성 참여를 확인하기 전에 사용자 행을 잠근다.")
  @Test
  void join_locksUserBeforeCheckingActiveParticipation() {
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(userRepository.findByIdForUpdate(3L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(3).build()));
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 3L))
        .willReturn(Optional.empty());
    given(explorationParticipantRepository.findActiveExplorationId(3L))
        .willReturn(Optional.of(44L));

    assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 3L));

    org.mockito.InOrder locks = Mockito.inOrder(userRepository, explorationParticipantRepository);
    locks.verify(userRepository).findByIdForUpdate(3L);
    locks.verify(explorationParticipantRepository).findActiveExplorationId(3L);
  }

  @DisplayName("만료된 공유 링크로 합류하면 예외가 발생한다.")
  @Test
  void join_expiredShareLink_throws() {
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().minusMinutes(1))));

    assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 2L));
  }

  @DisplayName("완료된 탐험에는 합류할 수 없다.")
  @Test
  void join_completedExploration_throwsConflict() {
    Exploration completedExploration =
        Exploration.builder().courseId(10L).status(ExplorationStatus.COMPLETED).build();
    ReflectionTestUtils.setField(completedExploration, "id", 5L);
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(completedExploration));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 2L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
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
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(existing));
    given(userRepository.findByIdForUpdate(2L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(2).build()));

    ExplorationDtos.JoinResponse response = explorationService.join(10L, 2L);

    assertThat(response.displayName()).isEqualTo("여행자");
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.alreadyJoined()).isTrue();
    assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
    Mockito.verify(explorationParticipantRepository, Mockito.never())
        .save(any(ExplorationParticipant.class));
    Mockito.verify(applicationEventPublisher, Mockito.never()).publishEvent(any());
  }

  @DisplayName("이미 활성 참여 중인 사용자가 다시 합류를 요청하면 기존 상태를 그대로 반환한다.")
  @Test
  void join_activeParticipant_returnsAlreadyJoined() {
    ExplorationParticipant existing =
        ExplorationParticipant.builder()
            .explorationId(5L)
            .userId(2L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("여행자")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.now().minusDays(1))
            .build();
    given(courseRepository.findById(10L))
        .willReturn(Optional.of(confirmedCourse(LocalDateTime.now().plusDays(1))));
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(existing));

    ExplorationDtos.JoinResponse response = explorationService.join(10L, 2L);

    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.alreadyJoined()).isTrue();
    Mockito.verify(explorationParticipantRepository, Mockito.never())
        .save(any(ExplorationParticipant.class));
    Mockito.verify(applicationEventPublisher, Mockito.never()).publishEvent(any());
  }

  @DisplayName("이탈했던 탐험에 재합류할 때도 다른 활성 탐험 참여가 있으면 차단된다.")
  @Test
  void join_leftParticipantWithActiveParticipationElsewhere_throws() {
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
    given(explorationRepository.findByCourseIdForUpdate(10L))
        .willReturn(Optional.of(exploration(5L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(existing));
    given(userRepository.findByIdForUpdate(2L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(2).build()));
    given(explorationParticipantRepository.findActiveExplorationId(2L))
        .willReturn(Optional.of(44L));

    assertThrows(ExplorationHandler.class, () -> explorationService.join(10L, 2L));

    assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.LEFT);
  }

  @DisplayName("현재 팀원 목록은 OWNER 우선으로 정렬되고 참여자별 고유 방문 수를 포함한다.")
  @Test
  void getParticipants_returnsCurrentParticipantsWithVisitCounts() {
    LocalDateTime ownerJoinedAt = LocalDateTime.of(2026, 8, 15, 18, 0);
    LocalDateTime meJoinedAt = LocalDateTime.of(2026, 8, 14, 9, 40);
    ExplorationParticipant owner =
        participant(
            70L, 7L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE, "여행자", true, ownerJoinedAt);
    ExplorationParticipant me =
        participant(
            71L, 8L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE, "별밤지기", false, meJoinedAt);
    ExplorationParticipant completed =
        participant(
            72L,
            9L,
            ParticipantRole.MEMBER,
            ParticipantStatus.COMPLETED,
            "빛고을",
            false,
            LocalDateTime.of(2026, 8, 16, 10, 0));
    ExplorationParticipant left =
        participant(
            73L,
            10L,
            ParticipantRole.MEMBER,
            ParticipantStatus.LEFT,
            "떠난사람",
            false,
            LocalDateTime.of(2026, 8, 13, 10, 0));
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 8L))
        .willReturn(Optional.of(me));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(left, me, completed, owner));
    given(visitRepository.countByParticipantIds(List.of(70L, 71L, 72L)))
        .willReturn(List.of(visitCount(70L, 3L), visitCount(71L, 2L)));

    ExplorationDtos.ParticipantsResponse response = explorationService.getParticipants(44L, 8L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.participantCount()).isEqualTo(3);
    assertThat(response.participants())
        .extracting(ExplorationDtos.ParticipantResponse::participantId)
        .containsExactly(70L, 71L, 72L);
    assertThat(response.participants().get(0).visitedPlaceCount()).isEqualTo(3L);
    assertThat(response.participants().get(0).locationSharingEnabled()).isTrue();
    assertThat(response.participants().get(0).joinedAt()).isEqualTo(toSeoulTime(ownerJoinedAt));
    assertThat(response.participants().get(1).visitedPlaceCount()).isEqualTo(2L);
    assertThat(response.participants().get(1).isMe()).isTrue();
    assertThat(response.participants().get(2).status()).isEqualTo("COMPLETED");
    assertThat(response.participants().get(2).visitedPlaceCount()).isZero();
  }

  @DisplayName("존재하지 않는 탐험의 팀원 목록은 조회할 수 없다.")
  @Test
  void getParticipants_missingExploration_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.getParticipants(44L, 8L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
  }

  @DisplayName("완료 상태 참여자는 현재 팀원 목록을 조회할 수 있다.")
  @Test
  void getParticipants_completedParticipant_returnsParticipants() {
    ExplorationParticipant completed =
        participant(
            72L,
            8L,
            ParticipantRole.MEMBER,
            ParticipantStatus.COMPLETED,
            "빛고을",
            false,
            LocalDateTime.of(2026, 8, 16, 10, 0));
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 8L))
        .willReturn(Optional.of(completed));
    given(explorationParticipantRepository.findByExplorationId(44L)).willReturn(List.of(completed));

    ExplorationDtos.ParticipantsResponse response = explorationService.getParticipants(44L, 8L);

    assertThat(response.participantCount()).isEqualTo(1);
    assertThat(response.participants().getFirst().isMe()).isTrue();
    assertThat(response.participants().getFirst().status()).isEqualTo("COMPLETED");
  }

  @DisplayName("이탈한 참여자는 팀원 목록을 조회할 수 없다.")
  @Test
  void getParticipants_leftParticipant_throwsForbidden() {
    ExplorationParticipant left =
        participant(
            73L,
            8L,
            ParticipantRole.MEMBER,
            ParticipantStatus.LEFT,
            "떠난사람",
            false,
            LocalDateTime.of(2026, 8, 13, 10, 0));
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 8L))
        .willReturn(Optional.of(left));

    ExplorationHandler exception =
        assertThrows(ExplorationHandler.class, () -> explorationService.getParticipants(44L, 8L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
  }

  @DisplayName("현재 참여자의 위치 공유 설정을 변경한다.")
  @Test
  void updateLocationSharing_changesCurrentParticipantSetting() {
    ExplorationParticipant participant =
        participant(
            71L,
            8L,
            ParticipantRole.MEMBER,
            ParticipantStatus.ACTIVE,
            "별밤지기",
            false,
            LocalDateTime.of(2026, 8, 14, 9, 40));
    LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 15, 21, 20);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant));
    Mockito.doAnswer(
            invocation -> {
              ReflectionTestUtils.setField(participant, "updatedAt", updatedAt);
              return null;
            })
        .when(explorationParticipantRepository)
        .flush();

    ExplorationDtos.LocationSharingResponse response =
        explorationService.updateLocationSharing(44L, 8L, true);

    assertThat(participant.isLocationSharingEnabled()).isTrue();
    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.participantId()).isEqualTo(71L);
    assertThat(response.locationSharingEnabled()).isTrue();
    assertThat(response.updatedAt()).isEqualTo(toSeoulTime(updatedAt));
    ArgumentCaptor<ExplorationDtos.LocationSharingChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(ExplorationDtos.LocationSharingChangedEvent.class);
    Mockito.verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    ExplorationDtos.LocationSharingChangedEvent event = eventCaptor.getValue();
    assertThat(event.eventId()).isNotNull();
    assertThat(event.eventType()).isEqualTo("LOCATION_SHARING_CHANGED");
    assertThat(event.explorationId()).isEqualTo(44L);
    assertThat(event.occurredAt()).isEqualTo(response.updatedAt());
    assertThat(event.data().participantId()).isEqualTo(71L);
    assertThat(event.data().enabled()).isTrue();
  }

  @DisplayName("같은 위치 공유 설정 요청은 저장 시각을 바꾸지 않는다.")
  @Test
  void updateLocationSharing_sameSetting_returnsCurrentStateWithoutWrite() {
    ExplorationParticipant participant =
        participant(
            71L,
            8L,
            ParticipantRole.MEMBER,
            ParticipantStatus.ACTIVE,
            "별밤지기",
            false,
            LocalDateTime.of(2026, 8, 14, 9, 40));
    LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 15, 20, 0);
    ReflectionTestUtils.setField(participant, "updatedAt", updatedAt);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant));

    ExplorationDtos.LocationSharingResponse response =
        explorationService.updateLocationSharing(44L, 8L, false);

    assertThat(response.locationSharingEnabled()).isFalse();
    assertThat(response.updatedAt()).isEqualTo(toSeoulTime(updatedAt));
    Mockito.verify(explorationParticipantRepository, Mockito.never()).flush();
    Mockito.verify(applicationEventPublisher, Mockito.never()).publishEvent(any());
  }

  @DisplayName("현재 사용자의 탐험 참여 정보가 없으면 찾을 수 없음으로 처리한다.")
  @Test
  void updateLocationSharing_missingParticipant_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.empty());

    ExplorationHandler exception =
        assertThrows(
            ExplorationHandler.class,
            () -> explorationService.updateLocationSharing(44L, 8L, true));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_PARTICIPANT_NOT_FOUND);
  }

  @DisplayName("활성 상태가 아닌 참여자는 위치 공유 설정을 바꿀 수 없다.")
  @Test
  void updateLocationSharing_inactiveParticipant_throwsForbidden() {
    ExplorationParticipant participant =
        participant(
            71L,
            8L,
            ParticipantRole.MEMBER,
            ParticipantStatus.LEFT,
            "별밤지기",
            false,
            LocalDateTime.of(2026, 8, 14, 9, 40));
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration(44L)));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant));

    ExplorationHandler exception =
        assertThrows(
            ExplorationHandler.class,
            () -> explorationService.updateLocationSharing(44L, 8L, true));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN);
  }

  @DisplayName("완료된 탐험의 위치 공유 설정은 바꿀 수 없다.")
  @Test
  void updateLocationSharing_completedExploration_throwsConflict() {
    Exploration completedExploration =
        Exploration.builder().courseId(10L).status(ExplorationStatus.COMPLETED).build();
    ReflectionTestUtils.setField(completedExploration, "id", 44L);
    ExplorationParticipant participant =
        participant(
            71L,
            8L,
            ParticipantRole.MEMBER,
            ParticipantStatus.COMPLETED,
            "별밤지기",
            true,
            LocalDateTime.of(2026, 8, 14, 9, 40));
    given(explorationRepository.findById(44L)).willReturn(Optional.of(completedExploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserIdForUpdate(44L, 8L))
        .willReturn(Optional.of(participant));

    ExplorationHandler exception =
        assertThrows(
            ExplorationHandler.class,
            () -> explorationService.updateLocationSharing(44L, 8L, false));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
  }

  @DisplayName("존재하지 않는 탐험의 위치 공유 설정은 바꿀 수 없다.")
  @Test
  void updateLocationSharing_missingExploration_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        assertThrows(
            ExplorationHandler.class,
            () -> explorationService.updateLocationSharing(44L, 8L, true));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
  }
}
