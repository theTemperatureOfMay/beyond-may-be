package com.example.beyond_may_be.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
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
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

  @InjectMocks private CourseService courseService;

  @Mock private CourseRepository courseRepository;
  @Mock private ExplorationRepository explorationRepository;
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private UserRepository userRepository;
  @Mock private CoursePlaceRepository coursePlaceRepository;
  @Mock private PlaceRepository placeRepository;

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
}
