package com.example.beyond_may_be.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.place.service.TourApiNearbyPlaceClient;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NearbyPlaceServiceTest {

  private static final BigDecimal LATITUDE = new BigDecimal("35.146600");
  private static final BigDecimal LONGITUDE = new BigDecimal("126.919900");

  @InjectMocks private NearbyPlaceService nearbyPlaceService;

  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository participantRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private TourApiNearbyPlaceClient tourApiNearbyPlaceClient;

  @DisplayName("진행 중 탐험의 활성 참여자는 코스 장소를 제외한 주변 장소를 거리순으로 조회한다.")
  @Test
  void getNearbyPlaces_returnsNearestTourApiPlacesExcludingCoursePlaces() {
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration()));
    given(participantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(participant()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(31L))
        .willReturn(List.of(coursePlace(10L)));
    given(tourApiNearbyPlaceClient.fetchNearbyPlaces(LATITUDE, LONGITUDE))
        .willReturn(List.of(item(1001L), item(1002L), item(1003L)));
    given(placeRepository.findExistingTourContentIds(List.of(1001L, 1002L, 1003L)))
        .willReturn(Set.of(1001L, 1002L, 1003L));
    given(placeRepository.findAllByActiveTrue())
        .willReturn(
            List.of(
                place(10L, 1001L, "코스 장소", "35.146700", "126.919900"),
                place(12L, 1002L, "두 번째 장소", "35.149300", "126.919900"),
                place(11L, 1003L, "첫 번째 장소", "35.147500", "126.919900")));

    PlaceDtos.NearbyPlacesResponse response =
        nearbyPlaceService.getNearbyPlaces(44L, 2L, LATITUDE, LONGITUDE);

    assertThat(response.places())
        .extracting(PlaceDtos.NearbyPlaceResponse::placeId)
        .containsExactly(11L, 12L);
    assertThat(response.places())
        .extracting(PlaceDtos.NearbyPlaceResponse::name)
        .containsExactly("첫 번째 장소", "두 번째 장소");
    assertThat(response.places())
        .extracting(PlaceDtos.NearbyPlaceResponse::distanceMeters)
        .allMatch(distance -> distance > 0 && distance <= 1_000);
  }

  @DisplayName("새 TourAPI 장소는 내부 장소로 저장한 뒤 반환한다.")
  @Test
  void getNearbyPlaces_savesNewTourApiPlaceBeforeReturningIt() {
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration()));
    given(participantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(participant()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(31L))
        .willReturn(List.of());
    given(tourApiNearbyPlaceClient.fetchNearbyPlaces(LATITUDE, LONGITUDE))
        .willReturn(List.of(item(2001L)));
    given(placeRepository.findExistingTourContentIds(List.of(2001L))).willReturn(Set.of());
    given(placeRepository.findAllByActiveTrue())
        .willReturn(List.of(place(21L, 2001L, "주변 장소", "35.147500", "126.919900")));

    PlaceDtos.NearbyPlacesResponse response =
        nearbyPlaceService.getNearbyPlaces(44L, 2L, LATITUDE, LONGITUDE);

    assertThat(response.places())
        .extracting(PlaceDtos.NearbyPlaceResponse::placeId)
        .containsExactly(21L);
    verify(placeRepository)
        .insertTourApiPlace(
            2001L,
            14,
            "주변 장소",
            "박물관",
            "ARTIST",
            "[\"문화관광\",\"전시시설\",\"박물관\"]",
            "광주광역시 동구",
            new BigDecimal("35.147500"),
            new BigDecimal("126.919900"),
            null);
  }

  @DisplayName("TourAPI 결과가 없으면 저장된 활성 장소를 같은 조건으로 반환한다.")
  @Test
  void getNearbyPlaces_fallsBackToStoredActivePlaces() {
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration()));
    given(participantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(participant()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(31L))
        .willReturn(List.of(coursePlace(10L)));
    given(tourApiNearbyPlaceClient.fetchNearbyPlaces(LATITUDE, LONGITUDE)).willReturn(List.of());
    given(placeRepository.findAllByActiveTrue())
        .willReturn(
            List.of(
                place(10L, 1001L, "코스 장소", "35.146700", "126.919900"),
                place(31L, 3001L, "가까운 저장 장소", "35.147500", "126.919900"),
                place(32L, 3002L, "먼 저장 장소", "35.149300", "126.919900"),
                place(34L, 3004L, "세 번째 저장 장소", "35.151100", "126.919900"),
                place(35L, 3005L, "네 번째 저장 장소", "35.152900", "126.919900"),
                place(33L, 3003L, "반경 밖 장소", "35.160000", "126.919900")));

    PlaceDtos.NearbyPlacesResponse response =
        nearbyPlaceService.getNearbyPlaces(44L, 2L, LATITUDE, LONGITUDE);

    assertThat(response.places())
        .extracting(PlaceDtos.NearbyPlaceResponse::placeId)
        .containsExactly(31L, 32L, 34L);
  }

  @DisplayName("TourAPI 후보가 전부 코스 장소이면 저장된 활성 장소로 대체한다.")
  @Test
  void getNearbyPlaces_fallsBackWhenTourApiCandidatesAreNotRecommendable() {
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration()));
    given(participantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(participant()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(31L))
        .willReturn(List.of(coursePlace(10L)));
    given(tourApiNearbyPlaceClient.fetchNearbyPlaces(LATITUDE, LONGITUDE))
        .willReturn(List.of(item(1001L)));
    given(placeRepository.findExistingTourContentIds(List.of(1001L))).willReturn(Set.of(1001L));
    given(placeRepository.findAllByActiveTrue())
        .willReturn(
            List.of(
                place(10L, 1001L, "코스 장소", "35.146700", "126.919900"),
                place(31L, null, "저장된 대체 장소", "35.147500", "126.919900")));

    PlaceDtos.NearbyPlacesResponse response =
        nearbyPlaceService.getNearbyPlaces(44L, 2L, LATITUDE, LONGITUDE);

    assertThat(response.places())
        .extracting(PlaceDtos.NearbyPlaceResponse::placeId)
        .containsExactly(31L);
  }

  @DisplayName("진행 중이 아닌 탐험은 주변 장소를 조회할 수 없다.")
  @Test
  void getNearbyPlaces_rejectsExplorationThatIsNotOngoing() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.BEFORE).build();
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration));

    assertThatThrownBy(() -> nearbyPlaceService.getNearbyPlaces(44L, 2L, LATITUDE, LONGITUDE))
        .isInstanceOfSatisfying(
            ExplorationHandler.class,
            exception ->
                assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_ONGOING));
    verifyNoInteractions(tourApiNearbyPlaceClient);
  }

  @DisplayName("활성 참여자가 아니면 주변 장소를 조회할 수 없다.")
  @Test
  void getNearbyPlaces_rejectsInactiveParticipant() {
    ExplorationParticipant inactive =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(2L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.LEFT)
            .displayName("여행자")
            .build();
    given(explorationRepository.findById(44L)).willReturn(Optional.of(exploration()));
    given(participantRepository.findByExplorationIdAndUserId(44L, 2L))
        .willReturn(Optional.of(inactive));

    assertThatThrownBy(() -> nearbyPlaceService.getNearbyPlaces(44L, 2L, LATITUDE, LONGITUDE))
        .isInstanceOfSatisfying(
            ExplorationHandler.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN));
    verifyNoInteractions(tourApiNearbyPlaceClient);
  }

  private Exploration exploration() {
    Exploration exploration =
        Exploration.builder().courseId(31L).status(ExplorationStatus.ONGOING).build();
    ReflectionTestUtils.setField(exploration, "id", 44L);
    return exploration;
  }

  private ExplorationParticipant participant() {
    return ExplorationParticipant.builder()
        .explorationId(44L)
        .userId(2L)
        .role(ParticipantRole.MEMBER)
        .status(ParticipantStatus.ACTIVE)
        .displayName("여행자")
        .build();
  }

  private CoursePlace coursePlace(long placeId) {
    return CoursePlace.builder()
        .courseId(31L)
        .placeId(placeId)
        .dayNumber(1)
        .visitOrder(1)
        .estimatedStayMinutes(60)
        .build();
  }

  private TourApiNearbyPlaceClient.NearbyItem item(long contentId) {
    return new TourApiNearbyPlaceClient.NearbyItem(
        "광주광역시 동구",
        "",
        Long.toString(contentId),
        "14",
        null,
        "126.919900",
        "35.147500",
        "5",
        "주변 장소",
        "VE",
        "VE07",
        "VE070100");
  }

  private Place place(long id, Long contentId, String name, String latitude, String longitude) {
    Place place =
        Place.builder()
            .name(name)
            .category("문화시설")
            .travelMbtiType(TravelPreferenceType.ARTIST)
            .tags(List.of("문화"))
            .address("광주광역시 동구")
            .latitude(new BigDecimal(latitude))
            .longitude(new BigDecimal(longitude))
            .tourContentId(contentId)
            .tourContentTypeId(14)
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }
}
