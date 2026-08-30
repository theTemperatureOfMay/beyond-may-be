package com.example.beyond_may_be.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

  @InjectMocks private VisitService visitService;

  @Mock private VisitRepository visitRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @DisplayName("주변 장소 방문은 개인별로 기록하고 기존 팀 방문에 따라 최초 여부만 바꾼다.")
  @ParameterizedTest
  @CsvSource({"false, true", "true, false"})
  void confirmVisit_nearbyPlace_recordsPersonalVisitWithTeamFirstStatus(
      boolean teammateVisited, boolean expectedTeamFirst) {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    ExplorationParticipant teammate = participant(73L, 10L, ParticipantStatus.ACTIVE);
    Place place = place(121L, "35.140200", "126.912400");
    Visit savedVisit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .coursePlaceId(null)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(savedVisit, "id", 9001L);

    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(placeRepository.findByIdAndActiveTrue(121L)).willReturn(Optional.of(place));
    given(visitRepository.existsByParticipantIdAndPlaceId(72L, 121L)).willReturn(false);
    given(coursePlaceRepository.findByCourseIdAndPlaceId(31L, 121L)).willReturn(Optional.empty());
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(participant, teammate));
    given(visitRepository.existsByPlaceIdAndParticipantIdIn(121L, List.of(72L, 73L)))
        .willReturn(teammateVisited);
    given(visitRepository.save(any(Visit.class))).willReturn(savedVisit);
    given(visitRepository.countDistinctCoursePlaceIds(List.of(72L, 73L))).willReturn(2L);
    given(coursePlaceRepository.countByCourseId(31L)).willReturn(5L);

    VisitDtos.ConfirmResponse response =
        visitService.confirmVisit(
            new VisitDtos.ConfirmRequest(
                44L,
                121L,
                new BigDecimal("35.140200"),
                new BigDecimal("126.912400"),
                new BigDecimal("18.5")),
            9L);

    assertThat(response.visitId()).isEqualTo(9001L);
    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.participantId()).isEqualTo(72L);
    assertThat(response.coursePlaceId()).isNull();
    assertThat(response.isCoursePlace()).isFalse();
    assertThat(response.distanceMeters()).isZero();
    assertThat(response.teamFirstVisit()).isEqualTo(expectedTeamFirst);
    assertThat(response.courseProgress().completedCoursePlaceCount()).isEqualTo(2L);
    assertThat(response.courseProgress().totalCoursePlaceCount()).isEqualTo(5L);
    assertThat(response.courseProgress().completionRate()).isEqualTo(40);
    assertThat(response.explorationStatus()).isEqualTo("ONGOING");

    ArgumentCaptor<VisitDtos.VisitConfirmedEvent> eventCaptor =
        ArgumentCaptor.forClass(VisitDtos.VisitConfirmedEvent.class);
    then(applicationEventPublisher).should().publishEvent(eventCaptor.capture());
    VisitDtos.VisitConfirmedEvent event = eventCaptor.getValue();
    assertThat(event.eventType()).isEqualTo("VISIT_CONFIRMED");
    assertThat(event.explorationId()).isEqualTo(44L);
    assertThat(event.occurredAt()).isEqualTo(response.visitedAt());
    assertThat(event.data().displayName()).isEqualTo("김감자감자");
    assertThat(event.data().coursePlaceId()).isNull();
    assertThat(event.data().teamFirstVisit()).isEqualTo(expectedTeamFirst);
    assertThat(event.data().courseProgress()).isEqualTo(response.courseProgress());
    assertThat(event.data().explorationStatus()).isEqualTo("ONGOING");
  }

  @DisplayName("인증 반경을 조금이라도 벗어나면 반올림 전 거리로 거부한다.")
  @Test
  void confirmVisit_justOutsideRadius_throwsOutOfRange() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(placeRepository.findByIdAndActiveTrue(121L))
        .willReturn(Optional.of(place(121L, "35.140200", "126.912400")));

    VisitHandler exception =
        catchThrowableOfType(
            VisitHandler.class,
            () ->
                visitService.confirmVisit(
                    new VisitDtos.ConfirmRequest(
                        44L,
                        121L,
                        new BigDecimal("35.141103"),
                        new BigDecimal("126.912400"),
                        new BigDecimal("18.5")),
                    9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VISIT_OUT_OF_RANGE);
  }

  @DisplayName("같은 참여자의 동일 장소 방문은 중복 기록하지 않는다.")
  @Test
  void confirmVisit_sameParticipantAndPlace_throwsAlreadyExists() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(placeRepository.findByIdAndActiveTrue(121L))
        .willReturn(Optional.of(place(121L, "35.140200", "126.912400")));
    given(visitRepository.existsByParticipantIdAndPlaceId(72L, 121L)).willReturn(true);

    VisitHandler exception =
        catchThrowableOfType(
            VisitHandler.class,
            () ->
                visitService.confirmVisit(
                    new VisitDtos.ConfirmRequest(
                        44L,
                        121L,
                        new BigDecimal("35.140200"),
                        new BigDecimal("126.912400"),
                        new BigDecimal("18.5")),
                    9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VISIT_ALREADY_EXISTS);
  }

  @DisplayName("활성 참여자가 아니면 방문 인증을 거부한다.")
  @Test
  void confirmVisit_inactiveParticipant_throwsForbidden() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant(72L, 9L, ParticipantStatus.LEFT)));

    ExplorationHandler exception =
        catchThrowableOfType(
            ExplorationHandler.class,
            () ->
                visitService.confirmVisit(
                    new VisitDtos.ConfirmRequest(
                        44L,
                        121L,
                        new BigDecimal("35.140200"),
                        new BigDecimal("126.912400"),
                        new BigDecimal("18.5")),
                    9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN);
  }

  @DisplayName("마지막 코스 장소를 팀 최초로 방문하면 탐험과 활성 참여자를 완료한다.")
  @Test
  void confirmVisit_lastCoursePlace_completesExplorationAndParticipants() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant current = participant(72L, 9L, ParticipantStatus.ACTIVE);
    ExplorationParticipant teammate = participant(73L, 10L, ParticipantStatus.ACTIVE);
    CoursePlace coursePlace =
        CoursePlace.builder()
            .courseId(31L)
            .placeId(121L)
            .dayNumber(1)
            .visitOrder(5)
            .estimatedStayMinutes(60)
            .build();
    ReflectionTestUtils.setField(coursePlace, "id", 301L);
    Visit savedVisit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .coursePlaceId(301L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(savedVisit, "id", 9001L);

    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(current));
    given(placeRepository.findByIdAndActiveTrue(121L))
        .willReturn(Optional.of(place(121L, "35.140200", "126.912400")));
    given(visitRepository.existsByParticipantIdAndPlaceId(72L, 121L)).willReturn(false);
    given(coursePlaceRepository.findByCourseIdAndPlaceId(31L, 121L))
        .willReturn(Optional.of(coursePlace));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(current, teammate));
    given(visitRepository.existsByPlaceIdAndParticipantIdIn(121L, List.of(72L, 73L)))
        .willReturn(false);
    given(visitRepository.save(any(Visit.class))).willReturn(savedVisit);
    given(visitRepository.countDistinctCoursePlaceIds(List.of(72L, 73L))).willReturn(5L);
    given(coursePlaceRepository.countByCourseId(31L)).willReturn(5L);

    VisitDtos.ConfirmResponse response =
        visitService.confirmVisit(
            new VisitDtos.ConfirmRequest(
                44L,
                121L,
                new BigDecimal("35.140200"),
                new BigDecimal("126.912400"),
                new BigDecimal("18.5")),
            9L);

    assertThat(response.coursePlaceId()).isEqualTo(301L);
    assertThat(response.teamFirstVisit()).isTrue();
    assertThat(response.courseProgress().completionRate()).isEqualTo(100);
    assertThat(response.explorationStatus()).isEqualTo("COMPLETED");
    assertThat(exploration.getStatus()).isEqualTo(ExplorationStatus.COMPLETED);
    assertThat(exploration.getCompletedAt()).isNotNull();
    assertThat(current.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
    assertThat(teammate.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    then(applicationEventPublisher).should(times(2)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues().get(0))
        .isInstanceOfSatisfying(
            VisitDtos.VisitConfirmedEvent.class,
            event -> assertThat(event.data().explorationStatus()).isEqualTo("COMPLETED"));
    assertThat(eventCaptor.getAllValues().get(1))
        .isInstanceOfSatisfying(
            ExplorationDtos.ExplorationCompletedEvent.class,
            event -> {
              assertThat(event.eventType()).isEqualTo("EXPLORATION_COMPLETED");
              assertThat(event.data().status()).isEqualTo("COMPLETED");
              assertThat(event.data().completionReason()).isEqualTo("ALL_COURSE_PLACES_VISITED");
            });
  }

  private Exploration exploration(ExplorationStatus status) {
    Exploration exploration = Exploration.builder().courseId(31L).status(status).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    return exploration;
  }

  private ExplorationParticipant participant(long id, long userId, ParticipantStatus status) {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(userId)
            .role(ParticipantRole.MEMBER)
            .status(status)
            .displayName("김감자감자")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.of(2026, 8, 14, 10, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }

  private Place place(long id, String latitude, String longitude) {
    Place place =
        Place.builder()
            .name("양림동 펭귄마을")
            .category("관광지")
            .travelMbtiType(TravelPreferenceType.REMEMBERER)
            .tags(List.of("골목", "역사"))
            .address("광주광역시 남구")
            .latitude(new BigDecimal(latitude))
            .longitude(new BigDecimal(longitude))
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }
}
