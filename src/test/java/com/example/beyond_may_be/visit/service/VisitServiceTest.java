package com.example.beyond_may_be.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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
import com.example.beyond_may_be.visit.domain.VisitPhoto;
import com.example.beyond_may_be.visit.dto.VisitDtos;
import com.example.beyond_may_be.visit.repository.VisitPhotoRepository;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

  @InjectMocks private VisitService visitService;

  @Mock private VisitRepository visitRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private VisitPhotoRepository visitPhotoRepository;
  @Mock private VisitPhotoStorage visitPhotoStorage;

  @DisplayName("과거 참여자는 코스 밖 장소와 사진을 포함한 팀 방문 기록을 시간 역순으로 조회한다.")
  @Test
  void getVisits_historicalParticipant_returnsTeamVisitsNewestFirst() {
    ExplorationParticipant current = participant(72L, 9L, ParticipantStatus.LEFT);
    ExplorationParticipant teammate = participant(73L, 10L, ParticipantStatus.COMPLETED);
    ReflectionTestUtils.setField(teammate, "displayName", "여행자");
    Visit recentVisit =
        Visit.builder()
            .participantId(73L)
            .placeId(101L)
            .coursePlaceId(301L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 15, 5, 40))
            .build();
    ReflectionTestUtils.setField(recentVisit, "id", 9002L);
    Visit nearbyVisit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(nearbyVisit, "id", 9001L);
    Place coursePlace = place(101L, "35.146702", "126.919998");
    ReflectionTestUtils.setField(coursePlace, "name", "국립아시아문화전당");
    ReflectionTestUtils.setField(coursePlace, "category", "문화시설");
    ReflectionTestUtils.setField(coursePlace, "travelMbtiType", TravelPreferenceType.ARTIST);
    ReflectionTestUtils.setField(coursePlace, "tags", List.of("전시", "문화"));
    ReflectionTestUtils.setField(coursePlace, "address", "광주광역시 동구 문화전당로 38");
    ReflectionTestUtils.setField(
        coursePlace, "thumbnailUrl", "https://example.com/places/101.webp");
    Place nearbyPlace = place(121L, "35.140200", "126.912400");
    VisitPhoto firstPhoto =
        VisitPhoto.builder().visitId(9001L).objectKey("visits/9001/first").displayOrder(1).build();
    ReflectionTestUtils.setField(firstPhoto, "id", 501L);
    VisitPhoto secondPhoto =
        VisitPhoto.builder().visitId(9001L).objectKey("visits/9001/second").displayOrder(2).build();
    ReflectionTestUtils.setField(secondPhoto, "id", 502L);

    given(explorationRepository.findById(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.COMPLETED)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(current));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(current, teammate));
    given(visitRepository.findByParticipantIdInOrderByVisitedAtDesc(List.of(72L, 73L)))
        .willReturn(List.of(recentVisit, nearbyVisit));
    given(placeRepository.findAllById(List.of(101L, 121L)))
        .willReturn(List.of(coursePlace, nearbyPlace));
    given(
            visitPhotoRepository.findByVisitIdInOrderByVisitIdAscDisplayOrderAsc(
                List.of(9002L, 9001L)))
        .willReturn(List.of(firstPhoto, secondPhoto));
    given(visitPhotoStorage.createSignedGetUrl("visits/9001/first"))
        .willReturn(
            new VisitPhotoStorage.SignedUrl(
                "https://example.com/signed/501", Instant.parse("2026-08-15T06:32:10Z")));
    given(visitPhotoStorage.createSignedGetUrl("visits/9001/second"))
        .willReturn(
            new VisitPhotoStorage.SignedUrl(
                "https://example.com/signed/502", Instant.parse("2026-08-15T06:34:00Z")));

    VisitDtos.VisitsResponse response = visitService.getVisits(44L, 9L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.totalCount()).isEqualTo(2);
    assertThat(response.visits())
        .extracting(VisitDtos.VisitResponse::visitId)
        .containsExactly(9002L, 9001L);
    VisitDtos.VisitResponse courseVisit = response.visits().get(0);
    assertThat(courseVisit.participant()).isEqualTo(new VisitDtos.ParticipantResponse(73L, "여행자"));
    assertThat(courseVisit.place().name()).isEqualTo("국립아시아문화전당");
    assertThat(courseVisit.place().travelMbtiType()).isEqualTo(TravelPreferenceType.ARTIST);
    assertThat(courseVisit.place().thumbnailUrl()).isEqualTo("https://example.com/places/101.webp");
    assertThat(courseVisit.coursePlaceId()).isEqualTo(301L);
    assertThat(courseVisit.isCoursePlace()).isTrue();
    assertThat(courseVisit.photos()).isEmpty();
    VisitDtos.VisitResponse nearby = response.visits().get(1);
    assertThat(nearby.participant()).isEqualTo(new VisitDtos.ParticipantResponse(72L, "김감자감자"));
    assertThat(nearby.coursePlaceId()).isNull();
    assertThat(nearby.isCoursePlace()).isFalse();
    assertThat(nearby.photos())
        .extracting(VisitDtos.VisitPhotoResponse::displayOrder)
        .containsExactly(1, 2);
    assertThat(nearby.photos().get(0).imageUrl()).isEqualTo("https://example.com/signed/501");
    assertThat(nearby.photos().get(0).urlExpiresAt())
        .isEqualTo(OffsetDateTime.parse("2026-08-15T15:32:10+09:00"));
  }

  @DisplayName("방문 기록이 없으면 빈 목록과 0건을 반환한다.")
  @Test
  void getVisits_noVisits_returnsEmptyResponse() {
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    given(explorationRepository.findById(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(participant));
    given(visitRepository.findByParticipantIdInOrderByVisitedAtDesc(List.of(72L)))
        .willReturn(List.of());

    VisitDtos.VisitsResponse response = visitService.getVisits(44L, 9L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.visits()).isEmpty();
    assertThat(response.totalCount()).isZero();
    then(placeRepository).shouldHaveNoInteractions();
    then(visitPhotoRepository).shouldHaveNoInteractions();
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("없는 탐험의 방문 기록 조회는 404로 거부한다.")
  @Test
  void getVisits_missingExploration_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        catchThrowableOfType(ExplorationHandler.class, () -> visitService.getVisits(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
    then(explorationParticipantRepository).shouldHaveNoInteractions();
  }

  @DisplayName("현재나 과거 참여자가 아니면 팀 방문 기록 조회를 403으로 거부한다.")
  @Test
  void getVisits_nonParticipant_throwsForbidden() {
    given(explorationRepository.findById(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.COMPLETED)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.empty());

    ExplorationHandler exception =
        catchThrowableOfType(ExplorationHandler.class, () -> visitService.getVisits(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
    then(visitRepository).shouldHaveNoInteractions();
  }

  @DisplayName("과거 참여자는 주변 장소를 포함한 팀 방문을 장소별 최근 방문순으로 조회한다.")
  @Test
  void getVisitedPlaces_historicalParticipant_returnsPlaceAggregatesNewestFirst() {
    ExplorationParticipant current = participant(72L, 9L, ParticipantStatus.LEFT);
    ExplorationParticipant teammate = participant(73L, 10L, ParticipantStatus.COMPLETED);
    ExplorationParticipant latestVisitor = participant(74L, 11L, ParticipantStatus.COMPLETED);
    ReflectionTestUtils.setField(teammate, "displayName", "별밤지기");
    ReflectionTestUtils.setField(latestVisitor, "displayName", "숲길산책");
    Visit latestCourseVisit =
        Visit.builder()
            .participantId(74L)
            .placeId(101L)
            .coursePlaceId(301L)
            .visitedAt(toRuntimeLocalTime("2026-08-15T16:10:20+09:00"))
            .build();
    Visit latestNearbyVisit =
        Visit.builder()
            .participantId(73L)
            .placeId(121L)
            .visitedAt(toRuntimeLocalTime("2026-08-15T15:05:40+09:00"))
            .build();
    Visit firstNearbyVisit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .visitedAt(toRuntimeLocalTime("2026-08-15T14:32:10+09:00"))
            .build();
    Visit middleCourseVisit =
        Visit.builder()
            .participantId(73L)
            .placeId(101L)
            .coursePlaceId(301L)
            .visitedAt(toRuntimeLocalTime("2026-08-15T13:00:00+09:00"))
            .build();
    Visit firstCourseVisit =
        Visit.builder()
            .participantId(72L)
            .placeId(101L)
            .coursePlaceId(301L)
            .visitedAt(toRuntimeLocalTime("2026-08-15T11:20:00+09:00"))
            .build();
    Place coursePlace = place(101L, "35.146600", "126.919900");
    ReflectionTestUtils.setField(coursePlace, "name", "국립아시아문화전당");
    ReflectionTestUtils.setField(coursePlace, "category", "문화시설");
    ReflectionTestUtils.setField(coursePlace, "travelMbtiType", TravelPreferenceType.ARTIST);
    ReflectionTestUtils.setField(
        coursePlace, "thumbnailUrl", "https://example.com/places/101.webp");
    Place nearbyPlace = place(121L, "35.140100", "126.912300");

    given(explorationRepository.findById(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.COMPLETED)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(current));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(current, teammate, latestVisitor));
    given(visitRepository.findByParticipantIdInOrderByVisitedAtDesc(List.of(72L, 73L, 74L)))
        .willReturn(
            List.of(
                latestCourseVisit,
                latestNearbyVisit,
                firstNearbyVisit,
                middleCourseVisit,
                firstCourseVisit));
    given(placeRepository.findAllById(List.of(101L, 121L)))
        .willReturn(List.of(coursePlace, nearbyPlace));

    VisitDtos.VisitedPlacesResponse response = visitService.getVisitedPlaces(44L, 9L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.totalVisitedPlaceCount()).isEqualTo(2);
    assertThat(response.visitedPlaces())
        .extracting(VisitDtos.VisitedPlaceResponse::placeId)
        .containsExactly(101L, 121L);
    VisitDtos.VisitedPlaceResponse course = response.visitedPlaces().get(0);
    assertThat(course.name()).isEqualTo("국립아시아문화전당");
    assertThat(course.category()).isEqualTo("문화시설");
    assertThat(course.travelMbtiType()).isEqualTo(TravelPreferenceType.ARTIST);
    assertThat(course.latitude()).isEqualByComparingTo("35.146600");
    assertThat(course.longitude()).isEqualByComparingTo("126.919900");
    assertThat(course.thumbnailUrl()).isEqualTo("https://example.com/places/101.webp");
    assertThat(course.isCoursePlace()).isTrue();
    assertThat(course.visitCount()).isEqualTo(3);
    assertThat(course.visitedByCount()).isEqualTo(3);
    assertThat(course.firstVisitedAt())
        .isEqualTo(OffsetDateTime.parse("2026-08-15T11:20:00+09:00"));
    assertThat(course.lastVisitedAt()).isEqualTo(OffsetDateTime.parse("2026-08-15T16:10:20+09:00"));
    assertThat(course.participantDisplayNames()).containsExactly("김감자감자", "별밤지기", "숲길산책");
    VisitDtos.VisitedPlaceResponse nearby = response.visitedPlaces().get(1);
    assertThat(nearby.isCoursePlace()).isFalse();
    assertThat(nearby.visitCount()).isEqualTo(2);
    assertThat(nearby.visitedByCount()).isEqualTo(2);
    assertThat(nearby.participantDisplayNames()).containsExactly("김감자감자", "별밤지기");
    then(visitPhotoRepository).shouldHaveNoInteractions();
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("밝힌 지도에 방문 기록이 없으면 빈 목록과 0건을 반환한다.")
  @Test
  void getVisitedPlaces_noVisits_returnsEmptyResponse() {
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    given(explorationRepository.findById(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.of(participant));
    given(explorationParticipantRepository.findByExplorationId(44L))
        .willReturn(List.of(participant));
    given(visitRepository.findByParticipantIdInOrderByVisitedAtDesc(List.of(72L)))
        .willReturn(List.of());

    VisitDtos.VisitedPlacesResponse response = visitService.getVisitedPlaces(44L, 9L);

    assertThat(response.explorationId()).isEqualTo(44L);
    assertThat(response.visitedPlaces()).isEmpty();
    assertThat(response.totalVisitedPlaceCount()).isZero();
    then(placeRepository).shouldHaveNoInteractions();
  }

  @DisplayName("없는 탐험의 밝힌 지도 조회는 404로 거부한다.")
  @Test
  void getVisitedPlaces_missingExploration_throwsNotFound() {
    given(explorationRepository.findById(44L)).willReturn(Optional.empty());

    ExplorationHandler exception =
        catchThrowableOfType(
            ExplorationHandler.class, () -> visitService.getVisitedPlaces(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_NOT_FOUND);
    then(explorationParticipantRepository).shouldHaveNoInteractions();
  }

  @DisplayName("현재나 과거 참여자가 아니면 밝힌 지도 조회를 403으로 거부한다.")
  @Test
  void getVisitedPlaces_nonParticipant_throwsForbidden() {
    given(explorationRepository.findById(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.COMPLETED)));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 9L))
        .willReturn(Optional.empty());

    ExplorationHandler exception =
        catchThrowableOfType(
            ExplorationHandler.class, () -> visitService.getVisitedPlaces(44L, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
    then(visitRepository).shouldHaveNoInteractions();
  }

  @DisplayName("활성 참여자 본인은 방문 사진을 다음 표시 순서로 첨부한다.")
  @ParameterizedTest(name = "{0}")
  @MethodSource("supportedImages")
  void attachPhoto_activeVisitOwner_savesNextOrderAndReturnsSignedUrl(
      String ignoredName, String contentType, byte[] content) {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    Visit visit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(visit, "id", 9001L);
    VisitPhoto savedPhoto =
        VisitPhoto.builder()
            .visitId(9001L)
            .objectKey("visits/9001/photo.png")
            .displayOrder(3)
            .build();
    ReflectionTestUtils.setField(savedPhoto, "id", 501L);
    ReflectionTestUtils.setField(
        savedPhoto, "createdAt", toRuntimeLocalTime("2026-08-15T14:33:00+09:00"));
    MockMultipartFile file = new MockMultipartFile("file", "visit", contentType, content);
    Instant expiresAt = Instant.parse("2026-08-15T06:33:00Z");

    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L)).willReturn(Optional.of(participant));
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(visitRepository.findByIdForUpdate(9001L)).willReturn(Optional.of(visit));
    given(visitPhotoRepository.findMaxDisplayOrderByVisitId(9001L)).willReturn(2);
    given(visitPhotoRepository.saveAndFlush(any(VisitPhoto.class))).willReturn(savedPhoto);
    given(visitPhotoStorage.createSignedGetUrl(any(String.class)))
        .willReturn(new VisitPhotoStorage.SignedUrl("https://example.com/signed/501", expiresAt));

    VisitDtos.PhotoResponse response = visitService.attachPhoto(9001L, file, 9L);

    assertThat(response.visitPhotoId()).isEqualTo(501L);
    assertThat(response.visitId()).isEqualTo(9001L);
    assertThat(response.displayOrder()).isEqualTo(3);
    assertThat(response.imageUrl()).isEqualTo("https://example.com/signed/501");
    assertThat(response.urlExpiresAt())
        .isEqualTo(OffsetDateTime.parse("2026-08-15T15:33:00+09:00"));
    assertThat(response.uploadedAt()).isEqualTo(OffsetDateTime.parse("2026-08-15T14:33:00+09:00"));
  }

  @DisplayName("없는 방문에는 사진을 첨부할 수 없다.")
  @Test
  void attachPhoto_missingVisit_throwsNotFound() {
    MockMultipartFile file = pngFile();
    given(visitRepository.findById(9001L)).willReturn(Optional.empty());

    VisitHandler exception =
        catchThrowableOfType(VisitHandler.class, () -> visitService.attachPhoto(9001L, file, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VISIT_NOT_FOUND);
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("방문을 만든 참여자와 다른 사용자는 사진을 첨부할 수 없다.")
  @Test
  void attachPhoto_differentUser_throwsForbidden() {
    Visit visit = visit(72L);
    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L))
        .willReturn(Optional.of(participant(72L, 10L, ParticipantStatus.ACTIVE)));

    ExplorationHandler exception =
        catchThrowableOfType(
            ExplorationHandler.class, () -> visitService.attachPhoto(9001L, pngFile(), 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
    then(explorationRepository).shouldHaveNoInteractions();
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("탐험에 남아 있지 않은 참여자는 본인의 방문에도 사진을 첨부할 수 없다.")
  @Test
  void attachPhoto_inactiveOwner_throwsForbidden() {
    Visit visit = visit(72L);
    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L))
        .willReturn(Optional.of(participant(72L, 9L, ParticipantStatus.LEFT)));
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));

    ExplorationHandler exception =
        catchThrowableOfType(
            ExplorationHandler.class, () -> visitService.attachPhoto(9001L, pngFile(), 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._FORBIDDEN);
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("S3 업로드가 실패하면 사진 메타데이터를 저장하지 않는다.")
  @Test
  void attachPhoto_storageUploadFails_doesNotSaveMetadata() {
    Visit visit = visit(72L);
    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L))
        .willReturn(Optional.of(participant(72L, 9L, ParticipantStatus.ACTIVE)));
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.ONGOING)));
    given(visitRepository.findByIdForUpdate(9001L)).willReturn(Optional.of(visit));
    given(visitPhotoRepository.findMaxDisplayOrderByVisitId(9001L)).willReturn(0);
    willThrow(new IllegalStateException("S3 업로드 실패"))
        .given(visitPhotoStorage)
        .upload(any(String.class), any(String.class), any(MockMultipartFile.class));

    IllegalStateException exception =
        catchThrowableOfType(
            IllegalStateException.class, () -> visitService.attachPhoto(9001L, pngFile(), 9L));

    assertThat(exception).hasMessage("S3 업로드 실패");
    then(visitPhotoRepository).should().findMaxDisplayOrderByVisitId(9001L);
    then(visitPhotoRepository).should(never()).saveAndFlush(any(VisitPhoto.class));
    then(visitRepository).should(times(1)).findByIdForUpdate(9001L);
  }

  @DisplayName("비어 있는 파일은 방문과 저장소를 조회하기 전에 거부한다.")
  @Test
  void attachPhoto_emptyFile_throwsBadRequest() {
    MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

    VisitHandler exception =
        catchThrowableOfType(VisitHandler.class, () -> visitService.attachPhoto(9001L, file, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus._BAD_REQUEST);
    then(visitRepository).shouldHaveNoInteractions();
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("사진이 10MB를 초과하면 저장 전에 413으로 거부한다.")
  @Test
  void attachPhoto_fileOverTenMegabytes_throwsPayloadTooLarge() {
    MockMultipartFile file =
        new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);

    VisitHandler exception =
        catchThrowableOfType(VisitHandler.class, () -> visitService.attachPhoto(9001L, file, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VISIT_PHOTO_TOO_LARGE);
    then(visitRepository).shouldHaveNoInteractions();
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("허용된 Content-Type을 주장해도 실제 이미지 형식이 아니면 415로 거부한다.")
  @Test
  void attachPhoto_fakeImageContent_throwsUnsupportedMediaType() {
    MockMultipartFile file =
        new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());

    VisitHandler exception =
        catchThrowableOfType(VisitHandler.class, () -> visitService.attachPhoto(9001L, file, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VISIT_PHOTO_UNSUPPORTED_TYPE);
    then(visitRepository).shouldHaveNoInteractions();
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("본인의 방문이어도 탐험이 완료됐다면 사진 첨부를 409로 거부한다.")
  @Test
  void attachPhoto_completedExploration_throwsConflict() {
    Visit visit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(visit, "id", 9001L);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "visit.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L))
        .willReturn(Optional.of(participant(72L, 9L, ParticipantStatus.COMPLETED)));
    given(explorationRepository.findByIdForUpdate(44L))
        .willReturn(Optional.of(exploration(ExplorationStatus.COMPLETED)));

    ExplorationHandler exception =
        catchThrowableOfType(
            ExplorationHandler.class, () -> visitService.attachPhoto(9001L, file, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
    then(visitPhotoStorage).shouldHaveNoInteractions();
  }

  @DisplayName("사진 표시 순서 저장이 충돌하면 업로드 객체를 삭제하고 409를 반환한다.")
  @Test
  void attachPhoto_displayOrderConflict_deletesObjectAndThrowsConflict() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    Visit visit =
        Visit.builder()
            .participantId(72L)
            .placeId(121L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(visit, "id", 9001L);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "visit.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L)).willReturn(Optional.of(participant));
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(visitRepository.findByIdForUpdate(9001L)).willReturn(Optional.of(visit));
    given(visitPhotoRepository.findMaxDisplayOrderByVisitId(9001L)).willReturn(0);
    given(visitPhotoRepository.saveAndFlush(any(VisitPhoto.class)))
        .willThrow(new DataIntegrityViolationException("표시 순서 충돌"));

    VisitHandler exception =
        catchThrowableOfType(VisitHandler.class, () -> visitService.attachPhoto(9001L, file, 9L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VISIT_PHOTO_ORDER_CONFLICT);
    then(visitPhotoStorage).should().delete(any(String.class));
  }

  @DisplayName("사진 메타데이터 저장 뒤 트랜잭션이 롤백되면 업로드 객체를 삭제한다.")
  @Test
  void attachPhoto_transactionRollsBackAfterSave_deletesUploadedObject() {
    Exploration exploration = exploration(ExplorationStatus.ONGOING);
    ExplorationParticipant participant = participant(72L, 9L, ParticipantStatus.ACTIVE);
    Visit visit = visit(72L);
    VisitPhoto savedPhoto =
        VisitPhoto.builder().visitId(9001L).objectKey("visits/9001/photo").displayOrder(1).build();
    ReflectionTestUtils.setField(savedPhoto, "id", 501L);
    ReflectionTestUtils.setField(savedPhoto, "createdAt", LocalDateTime.of(2026, 8, 15, 14, 33));
    given(visitRepository.findById(9001L)).willReturn(Optional.of(visit));
    given(explorationParticipantRepository.findById(72L)).willReturn(Optional.of(participant));
    given(explorationRepository.findByIdForUpdate(44L)).willReturn(Optional.of(exploration));
    given(visitRepository.findByIdForUpdate(9001L)).willReturn(Optional.of(visit));
    given(visitPhotoRepository.findMaxDisplayOrderByVisitId(9001L)).willReturn(0);
    given(visitPhotoRepository.saveAndFlush(any(VisitPhoto.class))).willReturn(savedPhoto);
    given(visitPhotoStorage.createSignedGetUrl(any(String.class)))
        .willReturn(
            new VisitPhotoStorage.SignedUrl(
                "https://example.com/signed/501", Instant.parse("2026-08-15T06:33:00Z")));

    TransactionSynchronizationManager.initSynchronization();
    try {
      visitService.attachPhoto(9001L, pngFile(), 9L);
      assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      then(visitPhotoStorage).should().delete(any(String.class));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

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

  private Visit visit(long participantId) {
    Visit visit =
        Visit.builder()
            .participantId(participantId)
            .placeId(121L)
            .visitedAt(LocalDateTime.of(2026, 8, 15, 14, 32, 10))
            .build();
    ReflectionTestUtils.setField(visit, "id", 9001L);
    return visit;
  }

  private static LocalDateTime toRuntimeLocalTime(String value) {
    return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
  }

  private MockMultipartFile pngFile() {
    return new MockMultipartFile(
        "file",
        "visit.png",
        "image/png",
        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
  }

  private static Stream<Arguments> supportedImages() {
    return Stream.of(
        Arguments.of("JPEG", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
        Arguments.of(
            "PNG", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
        Arguments.of(
            "WebP",
            "image/webp",
            new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}));
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
