package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.course.domain.CoursePlace;
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
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExplorationDetailServiceTest {

  @InjectMocks private ExplorationService explorationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private UserRepository userRepository;
  @Mock private VisitRepository visitRepository;

  @DisplayName("현재 또는 과거 참여자는 코스 ID로 탐험 ID를 조회한다.")
  @ParameterizedTest
  @EnumSource(ParticipantStatus.class)
  void getIdByCourseId_participant_returnsExplorationId(ParticipantStatus status) {
    given(explorationRepository.findByCourseId(31L))
        .willReturn(Optional.of(exploration(ExplorationStatus.BEFORE)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(participant(72L, 2L, ParticipantRole.MEMBER, status)));

    assertThat(explorationService.getIdByCourseId(31L, 2L).explorationId()).isEqualTo(44L);
  }

  @DisplayName("시작 전·진행 중·완료 탐험 모두 코스 ID로 조회한다.")
  @ParameterizedTest
  @EnumSource(ExplorationStatus.class)
  void getIdByCourseId_anyExplorationStatus_returnsExplorationId(ExplorationStatus status) {
    given(explorationRepository.findByCourseId(31L)).willReturn(Optional.of(exploration(status)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(
            Optional.of(participant(72L, 2L, ParticipantRole.OWNER, ParticipantStatus.COMPLETED)));

    assertThat(explorationService.getIdByCourseId(31L, 2L).explorationId()).isEqualTo(44L);
  }

  @DisplayName("코스에 연결된 탐험이 없으면 404 오류를 반환한다.")
  @Test
  void getIdByCourseId_missingExploration_throwsNotFound() {
    ExplorationHandler exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            ExplorationHandler.class, () -> explorationService.getIdByCourseId(31L, 2L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
  }

  @DisplayName("코스에 연결된 탐험의 참여자가 아니면 403 오류를 반환한다.")
  @Test
  void getIdByCourseId_nonParticipant_throwsForbidden() {
    given(explorationRepository.findByCourseId(31L))
        .willReturn(Optional.of(exploration(ExplorationStatus.BEFORE)));

    ExplorationHandler exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            ExplorationHandler.class, () -> explorationService.getIdByCourseId(31L, 2L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
  }

  private OffsetDateTime toSeoulTime(LocalDateTime value) {
    return value
        .atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
        .toOffsetDateTime();
  }

  @DisplayName("탐험 참여자는 팀 방문과 코스 진행률을 조회한다.")
  @Test
  void getDetail_participant_returnsTeamProgress() {
    LocalDateTime startedAt = LocalDateTime.of(2026, 8, 15, 10, 0);
    Exploration exploration =
        Exploration.builder()
            .courseId(31L)
            .status(ExplorationStatus.ONGOING)
            .startedByParticipantId(70L)
            .startedAt(startedAt)
            .build();
    ReflectionTestUtils.setField(exploration, "id", 44L);

    ExplorationParticipant owner =
        participant(70L, 1L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);
    ExplorationParticipant current =
        participant(72L, 2L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);
    ExplorationParticipant left =
        participant(73L, 3L, ParticipantRole.MEMBER, ParticipantStatus.LEFT);
    ExplorationParticipant completed =
        participant(74L, 4L, ParticipantRole.MEMBER, ParticipantStatus.COMPLETED);
    List<CoursePlace> coursePlaces =
        List.of(coursePlace(101L, 1L), coursePlace(102L, 2L), coursePlace(103L, 3L));
    List<Visit> visits =
        List.of(
            visit(70L, 1L, 101L),
            visit(72L, 1L, 101L),
            visit(73L, 2L, 102L),
            visit(72L, 99L, null),
            visit(72L, 100L, 999L));

    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(current));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(owner, current, left, completed));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(31L))
        .willReturn(coursePlaces);
    given(visitRepository.findByParticipantIdIn(anyList())).willReturn(visits);

    ExplorationDtos.DetailResponse response = explorationService.getDetail(44L, 2L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.courseId()).isEqualTo(31L);
    assertThat(response.status()).isEqualTo("ONGOING");
    assertThat(response.startedByParticipantId()).isEqualTo(70L);
    assertThat(response.startedAt()).isEqualTo(toSeoulTime(startedAt));
    assertThat(response.completedAt()).isNull();
    assertThat(response.participantCount()).isEqualTo(3);
    assertThat(response.teamVisitedPlaceCount()).isEqualTo(4);
    assertThat(response.courseProgress().completedCoursePlaceCount()).isEqualTo(2);
    assertThat(response.courseProgress().totalCoursePlaceCount()).isEqualTo(3);
    assertThat(response.courseProgress().completionRate()).isEqualTo(66);
    assertThat(response.currentParticipant().participantId()).isEqualTo(72L);
    assertThat(response.currentParticipant().role()).isEqualTo("MEMBER");
    assertThat(response.currentParticipant().status()).isEqualTo("ACTIVE");
    assertThat(response.currentParticipant().locationSharingEnabled()).isFalse();
    assertThat(response.permissions().canStart()).isFalse();
    assertThat(response.permissions().canCompleteEarly()).isFalse();
  }

  @DisplayName("ACTIVE 참여자는 시작 전 탐험을 시작할 수 있다.")
  @Test
  void getDetail_beforeActiveParticipant_canStart() {
    ExplorationParticipant current =
        participant(72L, 2L, ParticipantRole.MEMBER, ParticipantStatus.ACTIVE);

    ExplorationDtos.DetailResponse response = getDetail(ExplorationStatus.BEFORE, current);

    assertThat(response.courseProgress().completionRate()).isZero();
    assertThat(response.permissions().canStart()).isTrue();
    assertThat(response.permissions().canCompleteEarly()).isFalse();
  }

  @DisplayName("진행 중 탐험의 ACTIVE OWNER는 조기 완료할 수 있다.")
  @Test
  void getDetail_ongoingActiveOwner_canCompleteEarly() {
    ExplorationParticipant current =
        participant(70L, 2L, ParticipantRole.OWNER, ParticipantStatus.ACTIVE);

    ExplorationDtos.DetailResponse response = getDetail(ExplorationStatus.ONGOING, current);

    assertThat(response.permissions().canStart()).isFalse();
    assertThat(response.permissions().canCompleteEarly()).isTrue();
  }

  @DisplayName("LEFT 참여자도 과거 탐험을 조회할 수 있지만 실행 권한은 없다.")
  @Test
  void getDetail_leftParticipant_canReadWithoutPermissions() {
    ExplorationParticipant current =
        participant(72L, 2L, ParticipantRole.OWNER, ParticipantStatus.LEFT);

    ExplorationDtos.DetailResponse response = getDetail(ExplorationStatus.BEFORE, current);

    assertThat(response.participantCount()).isZero();
    assertThat(response.currentParticipant().status()).isEqualTo("LEFT");
    assertThat(response.permissions().canStart()).isFalse();
    assertThat(response.permissions().canCompleteEarly()).isFalse();
  }

  @DisplayName("탐험이 없으면 404 오류를 반환한다.")
  @Test
  void getDetail_missingExploration_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            ExplorationHandler.class, () -> explorationService.getDetail(44L, 2L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
  }

  @DisplayName("탐험 참여자가 아니면 403 오류를 반환한다.")
  @Test
  void getDetail_nonParticipant_throwsForbidden() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.empty());

    ExplorationHandler exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            ExplorationHandler.class, () -> explorationService.getDetail(44L, 2L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
  }

  private ExplorationDtos.DetailResponse getDetail(
      ExplorationStatus status, ExplorationParticipant current) {
    Exploration exploration = exploration(status);
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(current));
    given(explorationParticipantRepository.findByExplorationId(44L)).willReturn(List.of(current));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(31L))
        .willReturn(List.of());
    given(visitRepository.findByParticipantIdIn(anyList())).willReturn(List.of());
    return explorationService.getDetail(44L, 2L);
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
            .joinedAt(LocalDateTime.of(2026, 8, 14, 10, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }

  private CoursePlace coursePlace(long id, long placeId) {
    CoursePlace coursePlace =
        CoursePlace.builder()
            .courseId(31L)
            .placeId(placeId)
            .dayNumber(1)
            .visitOrder((int) (id - 100))
            .estimatedStayMinutes(60)
            .build();
    ReflectionTestUtils.setField(coursePlace, "id", id);
    return coursePlace;
  }

  private Visit visit(long participantId, long placeId, Long coursePlaceId) {
    return Visit.builder()
        .participantId(participantId)
        .placeId(placeId)
        .coursePlaceId(coursePlaceId)
        .visitedAt(LocalDateTime.of(2026, 8, 15, 11, 0))
        .build();
  }
}
