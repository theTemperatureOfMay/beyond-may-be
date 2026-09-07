package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
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
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.user.repository.UserRepository;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExplorationListServiceTest {

  @InjectMocks private ExplorationService explorationService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private CourseRepository courseRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private UserRepository userRepository;
  @Mock private VisitRepository visitRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @DisplayName("완료 탐험 목록은 팀 코스 진행률과 첫 대표 이미지를 조합한다.")
  @Test
  void getExplorations_completed_returnsCourseCards() {
    LocalDateTime startedAt = LocalDateTime.of(2026, 8, 15, 10, 0);
    LocalDateTime completedAt = LocalDateTime.of(2026, 8, 15, 18, 20);
    Exploration exploration =
        exploration(44L, 31L, ExplorationStatus.COMPLETED, startedAt, completedAt);
    Course course = course(31L, "하루치 광주");
    ExplorationParticipant owner =
        participant(70L, 44L, "여행자", ParticipantRole.OWNER, ParticipantStatus.COMPLETED, 9);
    ExplorationParticipant member =
        participant(71L, 44L, "별밤지기", ParticipantRole.MEMBER, ParticipantStatus.COMPLETED, 10);
    ExplorationParticipant left =
        participant(72L, 44L, "숲길산책", ParticipantRole.MEMBER, ParticipantStatus.LEFT, 11);
    List<CoursePlace> coursePlaces =
        List.of(
            coursePlace(101L, 31L, 201L, 1),
            coursePlace(102L, 31L, 202L, 2),
            coursePlace(103L, 31L, 203L, 3));

    given(
            explorationRepository.findAllByParticipantUserIdAndStatus(
                71L, ExplorationStatus.COMPLETED))
        .willReturn(List.of(exploration));
    given(courseRepository.findAllById(List.of(31L))).willReturn(List.of(course));
    given(explorationParticipantRepository.findByExplorationIdIn(List.of(44L)))
        .willReturn(List.of(member, left, owner));
    given(coursePlaceRepository.findByCourseIdInOrderByDayNumberAscVisitOrderAsc(List.of(31L)))
        .willReturn(coursePlaces);
    given(placeRepository.findAllById(List.of(201L, 202L, 203L)))
        .willReturn(
            List.of(
                place(203L, "https://example.com/places/103.webp"),
                place(201L, " "),
                place(202L, "https://example.com/places/102.webp")));
    given(visitRepository.findByParticipantIdIn(List.of(71L, 72L, 70L)))
        .willReturn(
            List.of(
                visit(70L, 201L, 101L),
                visit(71L, 201L, 101L),
                visit(72L, 202L, 102L),
                visit(70L, 999L, null),
                visit(70L, 998L, 999L)));

    ExplorationDtos.ExplorationsResponse response =
        explorationService.getExplorations(71L, ExplorationStatus.COMPLETED);

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.explorations())
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.explorationId()).isEqualTo(44L);
              assertThat(summary.courseId()).isEqualTo(31L);
              assertThat(summary.courseTitle()).isEqualTo("하루치 광주");
              assertThat(summary.status()).isEqualTo("COMPLETED");
              assertThat(summary.representativeImageUrl())
                  .isEqualTo("https://example.com/places/102.webp");
              assertThat(summary.participantCount()).isEqualTo(2);
              assertThat(summary.participantDisplayNames()).containsExactly("여행자", "별밤지기");
              assertThat(summary.completedCoursePlaceCount()).isEqualTo(2);
              assertThat(summary.totalCoursePlaceCount()).isEqualTo(3);
              assertThat(summary.startedAt())
                  .isEqualTo(
                      startedAt
                          .atZone(ZoneId.systemDefault())
                          .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                          .toOffsetDateTime());
              assertThat(summary.completedAt())
                  .isEqualTo(
                      completedAt
                          .atZone(ZoneId.systemDefault())
                          .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                          .toOffsetDateTime());
            });
  }

  @DisplayName("조회 결과가 없으면 빈 배열과 0건을 반환한다.")
  @Test
  void getExplorations_empty_returnsEmptyResponse() {
    given(explorationRepository.findAllByParticipantUserIdAndStatus(71L, ExplorationStatus.ONGOING))
        .willReturn(List.of());

    ExplorationDtos.ExplorationsResponse response =
        explorationService.getExplorations(71L, ExplorationStatus.ONGOING);

    assertThat(response.status()).isEqualTo("ONGOING");
    assertThat(response.explorations()).isEmpty();
    assertThat(response.totalCount()).isZero();
  }

  @DisplayName("BEFORE는 여행 기록 목록 상태로 허용하지 않는다.")
  @Test
  void getExplorations_before_throwsBadRequest() {
    ExplorationHandler exception =
        assertThrows(
            ExplorationHandler.class,
            () -> explorationService.getExplorations(71L, ExplorationStatus.BEFORE));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._BAD_REQUEST);
  }

  @DisplayName("진행 중 탐험이 둘 이상이면 목록 계약 위반으로 실패한다.")
  @Test
  void getExplorations_multipleOngoing_throwsAggregationFailure() {
    given(explorationRepository.findAllByParticipantUserIdAndStatus(71L, ExplorationStatus.ONGOING))
        .willReturn(
            List.of(
                exploration(
                    44L,
                    31L,
                    ExplorationStatus.ONGOING,
                    LocalDateTime.of(2026, 8, 15, 10, 0),
                    null),
                exploration(
                    45L,
                    32L,
                    ExplorationStatus.ONGOING,
                    LocalDateTime.of(2026, 8, 16, 10, 0),
                    null)));

    assertThrows(
        IllegalStateException.class,
        () -> explorationService.getExplorations(71L, ExplorationStatus.ONGOING));
  }

  private Exploration exploration(
      long id,
      long courseId,
      ExplorationStatus status,
      LocalDateTime startedAt,
      LocalDateTime completedAt) {
    Exploration exploration =
        Exploration.builder()
            .courseId(courseId)
            .status(status)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();
    ReflectionTestUtils.setField(exploration, "id", id);
    return exploration;
  }

  private Course course(long id, String title) {
    Course course =
        Course.builder()
            .ownerUserId(1L)
            .title(title)
            .status(CourseStatus.CONFIRMED)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 15))
            .endDate(LocalDate.of(2026, 8, 15))
            .startTime(LocalTime.of(9, 0))
            .build();
    ReflectionTestUtils.setField(course, "id", id);
    return course;
  }

  private ExplorationParticipant participant(
      long id,
      long explorationId,
      String displayName,
      ParticipantRole role,
      ParticipantStatus status,
      int joinedHour) {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(explorationId)
            .userId(id)
            .displayName(displayName)
            .role(role)
            .status(status)
            .joinedAt(LocalDateTime.of(2026, 8, 15, joinedHour, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", id);
    return participant;
  }

  private CoursePlace coursePlace(long id, long courseId, long placeId, int visitOrder) {
    CoursePlace coursePlace =
        CoursePlace.builder()
            .courseId(courseId)
            .placeId(placeId)
            .dayNumber(1)
            .visitOrder(visitOrder)
            .estimatedStayMinutes(60)
            .build();
    ReflectionTestUtils.setField(coursePlace, "id", id);
    return coursePlace;
  }

  private Place place(long id, String thumbnailUrl) {
    Place place =
        Place.builder()
            .name("장소 " + id)
            .category("관광")
            .travelMbtiType(TravelPreferenceType.ARTIST)
            .tags(List.of())
            .address("광주")
            .latitude(BigDecimal.valueOf(35.1))
            .longitude(BigDecimal.valueOf(126.9))
            .thumbnailUrl(thumbnailUrl)
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
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
