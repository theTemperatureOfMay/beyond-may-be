package com.example.beyond_may_be.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.exception.handler.VisitHandler;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.exploration.service.ExplorationBroadcastService;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

  @InjectMocks private VisitService visitService;

  @Mock private VisitRepository visitRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private ExplorationBroadcastService explorationBroadcastService;

  private ExplorationParticipant activeParticipant(long id) {
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
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }

  private Place placeAt(long id, double lat, double lon) {
    Place place =
        Place.builder()
            .name("장소")
            .category("카테고리")
            .travelMbtiType(TravelPreferenceType.THINKER)
            .address("광주")
            .latitude(BigDecimal.valueOf(lat))
            .longitude(BigDecimal.valueOf(lon))
            .businessHours("09:00-18:00")
            .description("설명")
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private Exploration exploration(long id, long courseId, ExplorationStatus status) {
    Exploration exploration = Exploration.builder().courseId(courseId).status(status).build();
    ReflectionTestUtils.setField(exploration, "id", id);
    return exploration;
  }

  @DisplayName("반경 100m 이내면 방문 인증이 성공한다.")
  @Test
  void confirmVisit_withinRadius_succeeds() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(activeParticipant(20L)));
    given(placeRepository.findById(100L)).willReturn(Optional.of(placeAt(100L, 35.15, 126.85)));
    given(visitRepository.existsByParticipantIdAndPlaceId(20L, 100L)).willReturn(false);
    given(explorationRepository.findById(5L))
        .willReturn(Optional.of(exploration(5L, 10L, ExplorationStatus.ONGOING)));
    given(coursePlaceRepository.findByCourseIdAndPlaceId(10L, 100L)).willReturn(Optional.empty());
    given(visitRepository.save(any(Visit.class))).willAnswer(inv -> inv.getArgument(0));

    VisitDtos.ConfirmResponse response =
        visitService.confirmVisit(
            5L, 2L, 100L, BigDecimal.valueOf(35.15), BigDecimal.valueOf(126.85));

    assertThat(response.placeId()).isEqualTo(100L);
    assertThat(response.teamFirstVisit()).isFalse();
    assertThat(response.explorationCompleted()).isFalse();
  }

  @DisplayName("반경 100m를 벗어나면 예외가 발생한다.")
  @Test
  void confirmVisit_outOfRange_throws() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(activeParticipant(20L)));
    given(placeRepository.findById(100L)).willReturn(Optional.of(placeAt(100L, 35.15, 126.85)));

    assertThrows(
        VisitHandler.class,
        () ->
            visitService.confirmVisit(
                5L, 2L, 100L, BigDecimal.valueOf(35.20), BigDecimal.valueOf(126.90)));
  }

  @DisplayName("이미 인증한 장소를 다시 인증하려 하면 예외가 발생한다.")
  @Test
  void confirmVisit_duplicate_throws() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(activeParticipant(20L)));
    given(placeRepository.findById(100L)).willReturn(Optional.of(placeAt(100L, 35.15, 126.85)));
    given(visitRepository.existsByParticipantIdAndPlaceId(20L, 100L)).willReturn(true);

    assertThrows(
        VisitHandler.class,
        () ->
            visitService.confirmVisit(
                5L, 2L, 100L, BigDecimal.valueOf(35.15), BigDecimal.valueOf(126.85)));
  }

  @DisplayName("코스 장소를 팀 최초로 인증하고 마지막 남은 장소였다면 탐험이 자동 완료된다.")
  @Test
  void confirmVisit_lastCoursePlaceTeamFirst_completesExploration() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(5L, 2L))
        .willReturn(Optional.of(activeParticipant(20L)));
    given(placeRepository.findById(100L)).willReturn(Optional.of(placeAt(100L, 35.15, 126.85)));
    given(visitRepository.existsByParticipantIdAndPlaceId(20L, 100L)).willReturn(false);
    given(explorationRepository.findById(5L))
        .willReturn(Optional.of(exploration(5L, 10L, ExplorationStatus.ONGOING)));

    com.example.beyond_may_be.course.domain.CoursePlace coursePlace =
        com.example.beyond_may_be.course.domain.CoursePlace.builder()
            .courseId(10L)
            .placeId(100L)
            .dayNumber(1)
            .visitOrder(1)
            .estimatedStayMinutes(60)
            .build();
    ReflectionTestUtils.setField(coursePlace, "id", 200L);
    given(coursePlaceRepository.findByCourseIdAndPlaceId(10L, 100L))
        .willReturn(Optional.of(coursePlace));
    given(explorationParticipantRepository.findParticipantIdsByExplorationId(5L))
        .willReturn(List.of(20L));
    given(visitRepository.existsByCoursePlaceIdAndParticipantIdIn(200L, List.of(20L)))
        .willReturn(false);
    given(visitRepository.save(any(Visit.class))).willAnswer(inv -> inv.getArgument(0));
    given(coursePlaceRepository.countByCourseId(10L)).willReturn(1L);
    given(visitRepository.countDistinctCoursePlaceIds(List.of(20L))).willReturn(1L);
    given(explorationRepository.completeIfOngoing(anyLong(), any())).willReturn(1);

    VisitDtos.ConfirmResponse response =
        visitService.confirmVisit(
            5L, 2L, 100L, BigDecimal.valueOf(35.15), BigDecimal.valueOf(126.85));

    assertThat(response.teamFirstVisit()).isTrue();
    assertThat(response.explorationCompleted()).isTrue();
  }
}
