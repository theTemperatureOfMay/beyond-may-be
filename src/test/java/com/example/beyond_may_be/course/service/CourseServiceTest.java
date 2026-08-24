package com.example.beyond_may_be.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.RecommendationHandler;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.course.repository.CourseRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.recommendation.repository.RecommendationSetRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

  @InjectMocks private CourseService courseService;

  @Mock private CourseRepository courseRepository;
  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private UserRepository userRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private PlaceRepository placeRepository;
  @Mock private GroqCourseChatClient groqCourseChatClient;
  @Mock private GroqCourseOrderClient groqCourseOrderClient;
  @Mock private RecommendationSetRepository recommendationSetRepository;
  @Mock private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUpTransactions() {
    Mockito.lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
            });
  }

  private Course draftCourse() {
    return Course.builder()
        .ownerUserId(1L)
        .title("광주 여행")
        .status(CourseStatus.DRAFT)
        .travelSchedule(TravelSchedule.DAY_TRIP)
        .startDate(LocalDate.of(2026, 8, 20))
        .endDate(LocalDate.of(2026, 8, 20))
        .startTime(LocalTime.of(9, 0))
        .aiRevisionCount(0)
        .build();
  }

  private Place placeAt(long id, double lat, String name) {
    return placeAt(id, lat, name, TravelPreferenceType.THINKER);
  }

  private Place placeAt(
      long id, double lat, String name, TravelPreferenceType travelPreferenceType) {
    Place place =
        Place.builder()
            .name(name)
            .category("카페")
            .travelMbtiType(travelPreferenceType)
            .address("광주")
            .latitude(BigDecimal.valueOf(lat))
            .longitude(BigDecimal.valueOf(126.85))
            .businessHours("09:00-18:00")
            .description("설명")
            .active(true)
            .build();
    ReflectionTestUtils.setField(place, "id", id);
    return place;
  }

  private void stubGeneration(
      RecommendationSet recommendationSet, List<Place> places, List<List<Long>> aiOrder) {
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(recommendationSet));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any())).willReturn(places);
    given(groqCourseOrderClient.order(any())).willReturn(aiOrder);
    given(userRepository.findByIdForUpdate(1L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(1).build()));
    given(courseRepository.save(any(Course.class)))
        .willAnswer(
            invocation -> {
              Course course = invocation.getArgument(0);
              ReflectionTestUtils.setField(course, "id", 10L);
              return course;
            });
    given(coursePlaceRepository.saveAll(Mockito.<List<CoursePlace>>any()))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @DisplayName("저장된 좋아요 장소를 AI가 정한 날짜별 순서로 DRAFT 코스에 저장한다.")
  @Test
  void generate_savesAiOrderedDraftCourse() {
    RecommendationSet recommendationSet =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.ONE_NIGHT_TWO_DAYS)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 21))
            .recommendedPlaceIds(List.of(1L, 2L, 3L, 4L, 5L))
            .likedPlaceIds(List.of(1L, 2L, 3L, 4L, 5L))
            .dislikedPlaceIds(List.of())
            .build();
    List<Place> places =
        List.of(
            placeAt(1L, 35.10, "장소1"),
            placeAt(2L, 35.11, "장소2"),
            placeAt(3L, 35.12, "장소3"),
            placeAt(4L, 35.13, "장소4"),
            placeAt(5L, 35.14, "장소5"));
    stubGeneration(recommendationSet, places, List.of(List.of(1L, 3L, 2L), List.of(5L, 4L)));

    CourseDtos.CourseDetailResponse response = courseService.generate(1L);

    assertThat(response.courseId()).isEqualTo(10L);
    assertThat(response.status()).isEqualTo("DRAFT");
    assertThat(response.startTime()).isEqualTo(LocalTime.of(7, 0));
    assertThat(response.places())
        .extracting(
            CourseDtos.CoursePlaceSummary::placeId,
            CourseDtos.CoursePlaceSummary::dayNumber,
            CourseDtos.CoursePlaceSummary::visitOrder)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1L, 1, 1),
            org.assertj.core.groups.Tuple.tuple(3L, 1, 2),
            org.assertj.core.groups.Tuple.tuple(2L, 1, 3),
            org.assertj.core.groups.Tuple.tuple(5L, 2, 1),
            org.assertj.core.groups.Tuple.tuple(4L, 2, 2));
  }

  @DisplayName("AI 순서가 유효하지 않으면 현재 장소에서 가장 가까운 장소 순으로 코스를 만든다.")
  @Test
  void generate_invalidAiOrder_usesNearestNeighborFallback() {
    RecommendationSet recommendationSet =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .recommendedPlaceIds(List.of(1L, 2L, 3L))
            .likedPlaceIds(List.of(1L, 2L, 3L))
            .dislikedPlaceIds(List.of())
            .build();
    List<Place> places =
        List.of(placeAt(1L, 35.00, "A"), placeAt(2L, 35.01, "B"), placeAt(3L, 35.20, "C"));
    stubGeneration(recommendationSet, places, List.of(List.of(1L, 2L, 999L)));

    CourseDtos.CourseDetailResponse response = courseService.generate(1L);

    assertThat(response.places().stream().map(CourseDtos.CoursePlaceSummary::placeId).toList())
        .containsExactly(1L, 2L, 3L);
  }

  @DisplayName("fallback은 FOODIE 장소를 점심 식사 순서에 먼저 배치한다.")
  @Test
  void generate_fallback_placesFoodieAtLunchSlot() {
    RecommendationSet recommendationSet =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .recommendedPlaceIds(List.of(1L, 2L, 3L))
            .likedPlaceIds(List.of(1L, 2L, 3L))
            .dislikedPlaceIds(List.of())
            .build();
    List<Place> places =
        List.of(
            placeAt(1L, 35.00, "A"),
            placeAt(2L, 35.01, "B"),
            placeAt(3L, 35.20, "식당", TravelPreferenceType.FOODIE));
    stubGeneration(recommendationSet, places, List.of());

    CourseDtos.CourseDetailResponse response = courseService.generate(1L);

    assertThat(response.places().stream().map(CourseDtos.CoursePlaceSummary::placeId).toList())
        .containsExactly(1L, 3L, 2L);
  }

  @DisplayName("AI가 날짜별 장소를 불균형하게 나누면 fallback이 1박 2일을 3개와 2개로 나눈다.")
  @Test
  void generate_unbalancedAiDays_usesBalancedFallback() {
    RecommendationSet recommendationSet =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.ONE_NIGHT_TWO_DAYS)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 21))
            .recommendedPlaceIds(List.of(1L, 2L, 3L, 4L, 5L))
            .likedPlaceIds(List.of(1L, 2L, 3L, 4L, 5L))
            .dislikedPlaceIds(List.of())
            .build();
    List<Place> places =
        List.of(
            placeAt(1L, 35.10, "장소1"),
            placeAt(2L, 35.11, "장소2"),
            placeAt(3L, 35.12, "장소3"),
            placeAt(4L, 35.13, "장소4"),
            placeAt(5L, 35.14, "장소5"));
    stubGeneration(recommendationSet, places, List.of(List.of(1L, 2L, 3L, 4L, 5L), List.of()));

    CourseDtos.CourseDetailResponse response = courseService.generate(1L);

    assertThat(response.places().stream().filter(place -> place.dayNumber() == 1)).hasSize(3);
    assertThat(response.places().stream().filter(place -> place.dayNumber() == 2)).hasSize(2);
  }

  @DisplayName("AI 호출 중 저장된 좋아요가 바뀌면 코스를 저장하지 않는다.")
  @Test
  void generate_selectionChangedDuringAiCall_rejectsSave() {
    RecommendationSet before =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .recommendedPlaceIds(List.of(1L, 2L, 3L))
            .likedPlaceIds(List.of(1L, 2L, 3L))
            .dislikedPlaceIds(List.of())
            .build();
    RecommendationSet changed =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .recommendedPlaceIds(List.of(1L, 2L, 3L))
            .likedPlaceIds(List.of(1L, 2L))
            .dislikedPlaceIds(List.of(3L))
            .build();
    List<Place> places =
        List.of(placeAt(1L, 35.00, "A"), placeAt(2L, 35.01, "B"), placeAt(3L, 35.02, "C"));
    given(recommendationSetRepository.findByUserId(1L))
        .willReturn(Optional.of(before), Optional.of(changed));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any())).willReturn(places);
    given(groqCourseOrderClient.order(any())).willReturn(List.of(List.of(1L, 2L, 3L)));
    given(userRepository.findByIdForUpdate(1L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(1).build()));

    RecommendationHandler exception =
        assertThrows(RecommendationHandler.class, () -> courseService.generate(1L));

    assertThat(exception.getCode()).isEqualTo(ErrorStatus.RECOMMENDATION_BATCH_CONFLICT);
    Mockito.verify(courseRepository, Mockito.never()).save(any());
  }

  @DisplayName("요청 thread가 저장 트랜잭션 진입 시 중단됐으면 DRAFT를 저장하지 않는다.")
  @Test
  void generate_interruptedBeforeSave_rejectsSave() {
    RecommendationSet recommendationSet =
        RecommendationSet.builder()
            .userId(1L)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 8, 20))
            .endDate(LocalDate.of(2026, 8, 20))
            .recommendedPlaceIds(List.of(1L, 2L, 3L))
            .likedPlaceIds(List.of(1L, 2L, 3L))
            .dislikedPlaceIds(List.of())
            .build();
    List<Place> places =
        List.of(placeAt(1L, 35.00, "A"), placeAt(2L, 35.01, "B"), placeAt(3L, 35.02, "C"));
    given(recommendationSetRepository.findByUserId(1L)).willReturn(Optional.of(recommendationSet));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any())).willReturn(places);
    given(groqCourseOrderClient.order(any())).willReturn(List.of(List.of(1L, 2L, 3L)));
    given(userRepository.findByIdForUpdate(1L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(1).build()));
    Mockito.doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
            })
        .when(transactionTemplate)
        .execute(any());

    try {
      CourseHandler exception = assertThrows(CourseHandler.class, () -> courseService.generate(1L));

      assertThat(exception.getCode()).isEqualTo(ErrorStatus.COURSE_GENERATION_TIMEOUT);
      Mockito.verify(courseRepository, Mockito.never()).save(any());
    } finally {
      Thread.interrupted();
    }
  }

  @DisplayName("소유자가 DRAFT 코스를 확정하면 Exploration과 OWNER Participant가 생성된다.")
  @Test
  void confirm_createsExplorationAndOwnerParticipant() {
    // given
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    given(explorationRepository.save(any(Exploration.class)))
        .willAnswer(
            invocation -> {
              Exploration exploration = invocation.getArgument(0);
              return Exploration.builder()
                  .courseId(exploration.getCourseId())
                  .status(exploration.getStatus())
                  .build();
            });
    given(userRepository.findById(1L))
        .willReturn(Optional.of(User.builder().nickname("여행자").identificationCode(1).build()));

    // when
    CourseDtos.ConfirmResponse response = courseService.confirm(10L, 1L);

    // then
    assertThat(response.status()).isEqualTo("CONFIRMED");
    assertThat(response.confirmedAt()).isNotNull();
    assertThat(response.shareExpiresAt()).isAfter(response.confirmedAt());
    assertThat(course.getStatus()).isEqualTo(CourseStatus.CONFIRMED);

    ArgumentCaptor<ExplorationParticipant> participantCaptor =
        ArgumentCaptor.forClass(ExplorationParticipant.class);
    Mockito.verify(explorationParticipantRepository).save(participantCaptor.capture());
    ExplorationParticipant savedParticipant = participantCaptor.getValue();
    assertThat(savedParticipant.getRole()).isEqualTo(ParticipantRole.OWNER);
    assertThat(savedParticipant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
    assertThat(savedParticipant.getDisplayName()).isEqualTo("여행자");
  }

  @DisplayName("존재하지 않는 코스를 확정하려 하면 예외가 발생한다.")
  @Test
  void confirm_courseNotFound_throws() {
    given(courseRepository.findById(99L)).willReturn(Optional.empty());

    assertThrows(CourseHandler.class, () -> courseService.confirm(99L, 1L));
  }

  @DisplayName("소유자가 아니면 확정할 수 없다.")
  @Test
  void confirm_notOwner_throws() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    assertThrows(CourseHandler.class, () -> courseService.confirm(10L, 2L));
  }

  @DisplayName("이미 확정된 코스는 다시 확정할 수 없다.")
  @Test
  void confirm_alreadyConfirmed_throws() {
    Course course = draftCourse();
    course.confirm(java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusDays(3));
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    assertThrows(CourseHandler.class, () -> courseService.confirm(10L, 1L));
  }

  @DisplayName("확정된 코스를 조회하면 장소가 일자·순서대로 반환된다.")
  @Test
  void getCourseDetail_confirmedCourse_returnsPlacesInOrder() {
    Course course = draftCourse();
    course.confirm(LocalDateTime.now(), LocalDateTime.now().plusDays(3));
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    CoursePlace coursePlace =
        CoursePlace.builder()
            .courseId(10L)
            .placeId(100L)
            .dayNumber(1)
            .visitOrder(1)
            .estimatedStayMinutes(60)
            .build();
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(coursePlace));

    Place place =
        Place.builder()
            .name("테스트 장소")
            .category("카페")
            .travelMbtiType(TravelPreferenceType.THINKER)
            .address("광주")
            .latitude(BigDecimal.valueOf(35.1595))
            .longitude(BigDecimal.valueOf(126.8526))
            .businessHours("09:00-18:00")
            .description("설명")
            .active(true)
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(place, "id", 100L);
    given(placeRepository.findAllById(List.of(100L))).willReturn(List.of(place));

    CourseDtos.CourseDetailResponse response = courseService.getCourseDetail(10L);

    assertThat(response.title()).isEqualTo("광주 여행");
    assertThat(response.places()).hasSize(1);
    assertThat(response.places().get(0).name()).isEqualTo("테스트 장소");
  }

  @DisplayName("CoursePlace가 가리키는 Place가 실제로 없어도 예외 없이 나머지 장소만 반환한다.")
  @Test
  void getCourseDetail_coursePlaceReferencesMissingPlace_skipsIt() {
    Course course = draftCourse();
    course.confirm(LocalDateTime.now(), LocalDateTime.now().plusDays(3));
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    CoursePlace missingPlaceRef =
        CoursePlace.builder()
            .courseId(10L)
            .placeId(999L) // places 테이블에 존재하지 않음 (FK 제약 없음)
            .dayNumber(1)
            .visitOrder(1)
            .estimatedStayMinutes(60)
            .build();
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(missingPlaceRef));
    given(placeRepository.findAllById(List.of(999L))).willReturn(List.of());

    CourseDtos.CourseDetailResponse response = courseService.getCourseDetail(10L);

    assertThat(response.places()).isEmpty();
  }

  @DisplayName("확정되지 않은 코스를 조회하면 예외가 발생한다.")
  @Test
  void getCourseDetail_notConfirmed_throws() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    assertThrows(CourseHandler.class, () -> courseService.getCourseDetail(10L));
  }

  @DisplayName("공유 링크가 만료된 코스를 조회하면 예외가 발생한다.")
  @Test
  void getCourseDetail_shareExpired_throws() {
    Course course = draftCourse();
    course.confirm(LocalDateTime.now().minusDays(4), LocalDateTime.now().minusDays(1));
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    assertThrows(ExplorationHandler.class, () -> courseService.getCourseDetail(10L));
  }

  private CoursePlace existingCoursePlace(long placeId, int day, int order, int stayMinutes) {
    return CoursePlace.builder()
        .courseId(10L)
        .placeId(placeId)
        .dayNumber(day)
        .visitOrder(order)
        .estimatedStayMinutes(stayMinutes)
        .travelModeFromPrevious(order == 1 ? null : "WALK")
        .build();
  }

  private void stubThreePlaceCourse() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    given(coursePlaceRepository.saveAll(Mockito.<List<CoursePlace>>any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              List<Place> places = new java.util.ArrayList<>();
              for (Long id : ids) {
                places.add(placeAt(id, 35.00, "장소" + id));
              }
              return places;
            });
  }

  @DisplayName("순서만 바꾸면 저장된 CoursePlace가 새 순서로 바뀌고 travelModeFromPrevious가 재계산된다.")
  @Test
  void editPlaces_reorderOnly_updatesOrderAndTravelMode() {
    stubThreePlaceCourse();
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(3L, 1, 1),
                new CourseDtos.PlaceOrderItem(1L, 1, 2),
                new CourseDtos.PlaceOrderItem(2L, 1, 3)));

    CourseDtos.CourseDetailResponse response = courseService.editPlaces(10L, 1L, request);

    assertThat(response.places().stream().map(CourseDtos.CoursePlaceSummary::placeId).toList())
        .containsExactly(3L, 1L, 2L);
    assertThat(response.places().get(0).travelModeFromPrevious()).isNull();
    assertThat(response.places().get(1).travelModeFromPrevious()).isEqualTo("WALK");
    assertThat(response.places().get(2).travelModeFromPrevious()).isEqualTo("WALK");
  }

  @DisplayName(
      "삭제를 먼저 flush한 뒤에 새 순서를 저장한다(IDENTITY 전략의 즉시 INSERT가"
          + " 아직 삭제되지 않은 (day,order) 슬롯과 충돌하지 않도록).")
  @Test
  void editPlaces_flushesDeleteBeforeSavingNewRows() {
    stubThreePlaceCourse();
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(3L, 1, 1),
                new CourseDtos.PlaceOrderItem(1L, 1, 2),
                new CourseDtos.PlaceOrderItem(2L, 1, 3)));

    courseService.editPlaces(10L, 1L, request);

    org.mockito.InOrder inOrder = Mockito.inOrder(coursePlaceRepository);
    inOrder.verify(coursePlaceRepository).deleteByCourseId(10L);
    inOrder.verify(coursePlaceRepository).flush();
    inOrder.verify(coursePlaceRepository).saveAll(Mockito.<List<CoursePlace>>any());
  }

  @DisplayName("장소를 일부 제외해도 최소 장소 수 이상이면 통과한다.")
  @Test
  void editPlaces_removeSomePlaces_succeedsAboveMinimum() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60),
                existingCoursePlace(4L, 1, 4, 20)));
    given(coursePlaceRepository.saveAll(Mockito.<List<CoursePlace>>any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              List<Place> places = new java.util.ArrayList<>();
              for (Long id : ids) {
                places.add(placeAt(id, 35.00, "장소" + id));
              }
              return places;
            });
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(1L, 1, 1),
                new CourseDtos.PlaceOrderItem(2L, 1, 2),
                new CourseDtos.PlaceOrderItem(3L, 1, 3)));

    CourseDtos.CourseDetailResponse response = courseService.editPlaces(10L, 1L, request);

    assertThat(response.places()).hasSize(3);
  }

  @DisplayName("최소 장소 수 미달이면 예외가 발생한다.")
  @Test
  void editPlaces_belowMinimumPlaceCount_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(List.of(new CourseDtos.PlaceOrderItem(1L, 1, 1)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 1L, request));
  }

  @DisplayName("요청에 중복 placeId가 있으면 예외가 발생한다.")
  @Test
  void editPlaces_duplicatePlaceIds_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(1L, 1, 1),
                new CourseDtos.PlaceOrderItem(1L, 1, 2),
                new CourseDtos.PlaceOrderItem(2L, 1, 3)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 1L, request));
  }

  @DisplayName("존재하지 않는 placeId가 포함되면 예외가 발생한다.")
  @Test
  void editPlaces_placeDoesNotExist_throwsNotFound() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any())).willReturn(List.of());
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(1L, 1, 1),
                new CourseDtos.PlaceOrderItem(2L, 1, 2),
                new CourseDtos.PlaceOrderItem(999L, 1, 3)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 1L, request));
  }

  @DisplayName("코스에 없던 placeId도 실제로 존재하면 추가되고 기본 체류시간이 채워진다.")
  @Test
  void editPlaces_addsNewExistingPlace_withDefaultStayMinutes() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willReturn(List.of(placeAt(4L, 35.00, "새 장소")));
    given(coursePlaceRepository.saveAll(Mockito.<List<CoursePlace>>any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(placeRepository.findAllById(List.of(1L, 2L, 3L, 4L)))
        .willReturn(
            List.of(
                placeAt(1L, 35.00, "장소1"),
                placeAt(2L, 35.00, "장소2"),
                placeAt(3L, 35.00, "장소3"),
                placeAt(4L, 35.00, "새 장소")));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(1L, 1, 1),
                new CourseDtos.PlaceOrderItem(2L, 1, 2),
                new CourseDtos.PlaceOrderItem(3L, 1, 3),
                new CourseDtos.PlaceOrderItem(4L, 1, 4)));

    CourseDtos.CourseDetailResponse response = courseService.editPlaces(10L, 1L, request);

    assertThat(response.places()).hasSize(4);
    CourseDtos.CoursePlaceSummary added =
        response.places().stream().filter(p -> p.placeId().equals(4L)).findFirst().orElseThrow();
    assertThat(added.estimatedStayMinutes()).isEqualTo(60);
  }

  @DisplayName("dayNumber가 여행 기간 범위를 벗어나면 예외가 발생한다.")
  @Test
  void editPlaces_dayOutOfRange_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(1L, 1, 1),
                new CourseDtos.PlaceOrderItem(2L, 2, 1), // DAY_TRIP은 1일뿐
                new CourseDtos.PlaceOrderItem(3L, 1, 2)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 1L, request));
  }

  @DisplayName("같은 일자·순서 조합이 중복되면 예외가 발생한다.")
  @Test
  void editPlaces_duplicateDayOrderSlot_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(1L, 1, 1),
                new CourseDtos.PlaceOrderItem(2L, 1, 1),
                new CourseDtos.PlaceOrderItem(3L, 1, 2)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 1L, request));
  }

  @DisplayName("소유자가 아니면 수정할 수 없다.")
  @Test
  void editPlaces_notOwner_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(List.of(new CourseDtos.PlaceOrderItem(1L, 1, 1)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 2L, request));
  }

  @DisplayName("이미 확정된 코스는 수정할 수 없다.")
  @Test
  void editPlaces_alreadyConfirmed_throws() {
    Course course = draftCourse();
    course.confirm(LocalDateTime.now(), LocalDateTime.now().plusDays(3));
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(List.of(new CourseDtos.PlaceOrderItem(1L, 1, 1)));

    assertThrows(CourseHandler.class, () -> courseService.editPlaces(10L, 1L, request));
  }

  @DisplayName("estimatedStayMinutes는 기존 값을 그대로 보존한다.")
  @Test
  void editPlaces_preservesEstimatedStayMinutes() {
    stubThreePlaceCourse();
    CourseDtos.UpdatePlacesRequest request =
        new CourseDtos.UpdatePlacesRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(3L, 1, 1),
                new CourseDtos.PlaceOrderItem(1L, 1, 2),
                new CourseDtos.PlaceOrderItem(2L, 1, 3)));

    CourseDtos.CourseDetailResponse response = courseService.editPlaces(10L, 1L, request);

    Map<Long, Integer> stayByPlaceId =
        response.places().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CourseDtos.CoursePlaceSummary::placeId,
                    CourseDtos.CoursePlaceSummary::estimatedStayMinutes));
    assertThat(stayByPlaceId).containsEntry(1L, 30).containsEntry(2L, 45).containsEntry(3L, 60);
  }

  @DisplayName("소유자가 DRAFT 코스를 조회하면 장소가 반환된다.")
  @Test
  void getDraftDetail_ownerDraftCourse_returnsPlaces() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(existingCoursePlace(1L, 1, 1, 30)));
    given(placeRepository.findAllById(List.of(1L))).willReturn(List.of(placeAt(1L, 35.00, "A")));

    CourseDtos.CourseDetailResponse response = courseService.getDraftDetail(10L, 1L);

    assertThat(response.status()).isEqualTo("DRAFT");
    assertThat(response.places()).hasSize(1);
  }

  @DisplayName("소유자가 아니면 DRAFT 코스를 조회할 수 없다.")
  @Test
  void getDraftDetail_notOwner_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));

    assertThrows(CourseHandler.class, () -> courseService.getDraftDetail(10L, 2L));
  }

  @DisplayName("이미 확정된 코스는 DRAFT 조회 대상이 아니다.")
  @Test
  void getDraftDetail_alreadyConfirmed_throws() {
    Course course = draftCourse();
    course.confirm(LocalDateTime.now(), LocalDateTime.now().plusDays(3));
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    assertThrows(CourseHandler.class, () -> courseService.getDraftDetail(10L, 1L));
  }

  @DisplayName("존재하지 않는 코스를 DRAFT 조회하면 예외가 발생한다.")
  @Test
  void getDraftDetail_notFound_throws() {
    given(courseRepository.findById(99L)).willReturn(Optional.empty());

    assertThrows(CourseHandler.class, () -> courseService.getDraftDetail(99L, 1L));
  }

  @DisplayName("COURSE_REVISION 응답이면 미리보기를 반환하고 aiRevisionCount만 증가한다(저장 없음).")
  @Test
  void requestChatRevision_courseRevision_returnsPreviewWithoutPersisting() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              List<Place> places = new java.util.ArrayList<>();
              for (Long id : ids) {
                places.add(placeAt(id, 35.00, "장소" + id));
              }
              return places;
            });
    given(placeRepository.findByActiveTrue()).willReturn(List.of());
    given(groqCourseChatClient.requestRevision(any(), any(), any(), any(), any()))
        .willReturn(
            Optional.of(
                new GroqCourseChatClient.ChatSuggestion(
                    "COURSE_REVISION", "순서를 바꿨어요", List.of(List.of(3L, 1L, 2L)), List.of())));

    CourseDtos.ChatResponse response =
        courseService.requestChatRevision(10L, 1L, new CourseDtos.ChatRequest("야경 명소 앞에 넣어줘"));

    assertThat(response.type()).isEqualTo("COURSE_REVISION");
    assertThat(
            response.proposedPlaces().stream().map(CourseDtos.CoursePlaceSummary::placeId).toList())
        .containsExactly(3L, 1L, 2L);
    assertThat(response.remainingRevisions()).isEqualTo(1);
    assertThat(course.getAiRevisionCount()).isEqualTo(1);
    Mockito.verify(coursePlaceRepository, Mockito.never()).saveAll(Mockito.anyList());
    Mockito.verify(coursePlaceRepository, Mockito.never()).deleteByCourseId(Mockito.anyLong());
  }

  @DisplayName("ADD_RECOMMENDATION 응답이면 추천 목록을 반환하고 aiRevisionCount가 증가한다.")
  @Test
  void requestChatRevision_addRecommendation_returnsRecommendations() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(existingCoursePlace(1L, 1, 1, 30)));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willReturn(List.of(placeAt(1L, 35.00, "A")));
    given(placeRepository.findByActiveTrue()).willReturn(List.of(placeAt(5L, 35.00, "양림동 카페")));
    given(groqCourseChatClient.requestRevision(any(), any(), any(), any(), any()))
        .willReturn(
            Optional.of(
                new GroqCourseChatClient.ChatSuggestion(
                    "ADD_RECOMMENDATION",
                    "카페를 추천해요",
                    List.of(),
                    List.of(new GroqCourseChatClient.Recommendation(5L, "조용해요")))));

    CourseDtos.ChatResponse response =
        courseService.requestChatRevision(10L, 1L, new CourseDtos.ChatRequest("카페 추가"));

    assertThat(response.type()).isEqualTo("ADD_RECOMMENDATION");
    assertThat(response.recommendations()).hasSize(1);
    assertThat(response.recommendations().get(0).placeId()).isEqualTo(5L);
    assertThat(response.recommendations().get(0).name()).isEqualTo("양림동 카페");
    assertThat(course.getAiRevisionCount()).isEqualTo(1);
  }

  @DisplayName("메시지가 150자를 초과하면 예외가 발생하고 Groq를 호출하지 않는다.")
  @Test
  void requestChatRevision_messageTooLong_throwsWithoutCallingGroq() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    String longMessage = "가".repeat(151);

    assertThrows(
        CourseHandler.class,
        () -> courseService.requestChatRevision(10L, 1L, new CourseDtos.ChatRequest(longMessage)));
    Mockito.verifyNoInteractions(groqCourseChatClient);
  }

  @DisplayName("AI 수정 요청 횟수를 모두 사용하면 예외가 발생하고 Groq를 호출하지 않는다.")
  @Test
  void requestChatRevision_revisionLimitExceeded_throwsWithoutCallingGroq() {
    Course course = draftCourse();
    ReflectionTestUtils.setField(course, "aiRevisionCount", 2);
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));

    assertThrows(
        CourseHandler.class,
        () -> courseService.requestChatRevision(10L, 1L, new CourseDtos.ChatRequest("아무거나")));
    Mockito.verifyNoInteractions(groqCourseChatClient);
  }

  @DisplayName("Groq 호출이 실패하면 예외가 발생하고 aiRevisionCount는 증가하지 않는다.")
  @Test
  void requestChatRevision_groqFails_throwsWithoutIncrementingCount() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(existingCoursePlace(1L, 1, 1, 30)));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willReturn(List.of(placeAt(1L, 35.00, "A")));
    given(placeRepository.findByActiveTrue()).willReturn(List.of());
    given(groqCourseChatClient.requestRevision(any(), any(), any(), any(), any()))
        .willReturn(Optional.empty());

    assertThrows(
        CourseHandler.class,
        () -> courseService.requestChatRevision(10L, 1L, new CourseDtos.ChatRequest("아무거나")));
    assertThat(course.getAiRevisionCount()).isEqualTo(0);
  }

  @DisplayName("챗봇 제안을 적용하면 직접 수정과 동일한 검증을 거쳐 저장된다.")
  @Test
  void applyChatRevision_appliesWithSameValidationAsEditPlaces() {
    stubThreePlaceCourse();
    CourseDtos.ApplyChatRevisionRequest request =
        new CourseDtos.ApplyChatRevisionRequest(
            List.of(
                new CourseDtos.PlaceOrderItem(3L, 1, 1),
                new CourseDtos.PlaceOrderItem(1L, 1, 2),
                new CourseDtos.PlaceOrderItem(2L, 1, 3)));

    CourseDtos.CourseDetailResponse response = courseService.applyChatRevision(10L, 1L, request);

    assertThat(response.places().stream().map(CourseDtos.CoursePlaceSummary::placeId).toList())
        .containsExactly(3L, 1L, 2L);
  }

  @DisplayName("챗봇 제안 적용도 최소 장소 수 미달이면 거부된다.")
  @Test
  void applyChatRevision_belowMinimumPlaceCount_throws() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    CourseDtos.ApplyChatRevisionRequest request =
        new CourseDtos.ApplyChatRevisionRequest(List.of(new CourseDtos.PlaceOrderItem(1L, 1, 1)));

    assertThrows(CourseHandler.class, () -> courseService.applyChatRevision(10L, 1L, request));
  }

  @DisplayName("추천 장소를 추가하면 Groq가 재배치한 순서로 저장되고 aiRevisionCount는 변하지 않는다.")
  @Test
  void addRecommendedPlace_addsAndReplacesWithGroqOrder() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(
            List.of(
                existingCoursePlace(1L, 1, 1, 30),
                existingCoursePlace(2L, 1, 2, 45),
                existingCoursePlace(3L, 1, 3, 60)));
    given(placeRepository.findById(4L)).willReturn(Optional.of(placeAt(4L, 35.00, "새 장소")));
    given(groqCourseChatClient.requestPlacementRevision(any(), any(), any(), any()))
        .willReturn(Optional.of(List.of(List.of(1L, 4L, 2L, 3L))));
    given(placeRepository.findAllById(Mockito.<Iterable<Long>>any()))
        .willReturn(List.of(placeAt(4L, 35.00, "새 장소")));
    given(coursePlaceRepository.saveAll(Mockito.<List<CoursePlace>>any()))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(placeRepository.findAllById(List.of(1L, 4L, 2L, 3L)))
        .willReturn(
            List.of(
                placeAt(1L, 35.00, "장소1"),
                placeAt(4L, 35.00, "새 장소"),
                placeAt(2L, 35.00, "장소2"),
                placeAt(3L, 35.00, "장소3")));

    CourseDtos.CourseDetailResponse response = courseService.addRecommendedPlace(10L, 1L, 4L);

    assertThat(response.places().stream().map(CourseDtos.CoursePlaceSummary::placeId).toList())
        .containsExactly(1L, 4L, 2L, 3L);
    CourseDtos.CoursePlaceSummary added =
        response.places().stream().filter(p -> p.placeId().equals(4L)).findFirst().orElseThrow();
    assertThat(added.estimatedStayMinutes()).isEqualTo(60);
    assertThat(course.getAiRevisionCount()).isEqualTo(0);
  }

  @DisplayName("이미 코스에 있는 장소를 추가하려 하면 예외가 발생하고 Groq를 호출하지 않는다.")
  @Test
  void addRecommendedPlace_alreadyInCourse_throwsWithoutCallingGroq() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(existingCoursePlace(1L, 1, 1, 30)));

    assertThrows(CourseHandler.class, () -> courseService.addRecommendedPlace(10L, 1L, 1L));
    Mockito.verifyNoInteractions(groqCourseChatClient);
  }

  @DisplayName("존재하지 않는 장소를 추가하려 하면 예외가 발생하고 Groq를 호출하지 않는다.")
  @Test
  void addRecommendedPlace_placeNotFound_throwsWithoutCallingGroq() {
    given(courseRepository.findById(10L)).willReturn(Optional.of(draftCourse()));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(existingCoursePlace(1L, 1, 1, 30)));
    given(placeRepository.findById(999L)).willReturn(Optional.empty());

    assertThrows(CourseHandler.class, () -> courseService.addRecommendedPlace(10L, 1L, 999L));
    Mockito.verifyNoInteractions(groqCourseChatClient);
  }

  @DisplayName("Groq 삽입 재배치가 실패하면 예외가 발생하고 aiRevisionCount는 변하지 않는다.")
  @Test
  void addRecommendedPlace_groqFails_throwsWithoutChangingCount() {
    Course course = draftCourse();
    given(courseRepository.findById(10L)).willReturn(Optional.of(course));
    given(coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(10L))
        .willReturn(List.of(existingCoursePlace(1L, 1, 1, 30)));
    given(placeRepository.findById(4L)).willReturn(Optional.of(placeAt(4L, 35.00, "새 장소")));
    given(groqCourseChatClient.requestPlacementRevision(any(), any(), any(), any()))
        .willReturn(Optional.empty());

    assertThrows(CourseHandler.class, () -> courseService.addRecommendedPlace(10L, 1L, 4L));
    assertThat(course.getAiRevisionCount()).isEqualTo(0);
  }
}
