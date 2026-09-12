package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.example.beyond_may_be.exploration.service.ExplorationService;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = BeyondMayBeApplication.class)
@Transactional
class ExplorationParticipationIntegrationTest {
  @Autowired private ExplorationService service;
  @Autowired private ExplorationRepository explorations;
  @Autowired private ExplorationParticipantRepository participants;
  @Autowired private CourseRepository courses;
  @Autowired private UserRepository users;
  @Autowired private com.example.beyond_may_be.visit.repository.VisitRepository visits;
  @Autowired private com.example.beyond_may_be.visit.repository.VisitPhotoRepository photos;

  @ParameterizedTest
  @EnumSource(
      value = ExplorationStatus.class,
      names = {"BEFORE", "ONGOING"})
  void duplicateThenLeaveJoinAndRejoinPreservesIdentityAndTransferredOwner(
      ExplorationStatus status) {
    var user = users.save(new User("이탈테스트", 1001));
    var successor = users.save(new User("승계테스트", 1002));
    var a = exploration(user.getId(), status);
    var oldParticipation = participant(a.getId(), user.getId(), ParticipantRole.OWNER);
    var visit =
        visits.saveAndFlush(
            com.example.beyond_may_be.visit.domain.Visit.builder()
                .participantId(oldParticipation.getId())
                .placeId(999992L)
                .visitedAt(LocalDateTime.now())
                .build());
    var photo =
        photos.saveAndFlush(
            com.example.beyond_may_be.visit.domain.VisitPhoto.builder()
                .visitId(visit.getId())
                .objectKey("visits/" + visit.getId() + "/test")
                .displayOrder(1)
                .build());
    var nextOwner = participant(a.getId(), successor.getId(), ParticipantRole.MEMBER);
    var b = exploration(999991L, ExplorationStatus.BEFORE);
    participant(b.getId(), 999991L, ParticipantRole.OWNER);

    var duplicate =
        assertThrows(ExplorationHandler.class, () -> service.join(b.getCourseId(), user.getId()));
    assertThat(duplicate.getData())
        .isEqualTo(new ExplorationDtos.ActiveExplorationResponse(a.getId()));
    assertThat(service.leave(a.getId(), user.getId()).ownerParticipantId())
        .isEqualTo(nextOwner.getId());
    assertThat(service.join(b.getCourseId(), user.getId()).explorationId()).isEqualTo(b.getId());
    assertThat(participants.findActiveExplorationId(user.getId())).contains(b.getId());
    assertThat(participants.findById(oldParticipation.getId())).isPresent();
    assertThat(visits.findById(visit.getId())).isPresent();
    assertThat(photos.findById(photo.getId())).isPresent();
    assertThat(participants.findExplorationIdByIdAndUserId(oldParticipation.getId(), user.getId()))
        .contains(a.getId());
    assertThat(
            participants.findExplorationIdByIdAndUserId(
                oldParticipation.getId(), successor.getId()))
        .isEmpty();

    service.leave(b.getId(), user.getId());
    var rejoined = service.join(a.getCourseId(), user.getId());
    assertThat(rejoined.participantId()).isEqualTo(oldParticipation.getId());
    assertThat(rejoined.role()).isEqualTo("MEMBER");
    assertThat(rejoined.locationSharingEnabled()).isFalse();
    assertThat(nextOwner.getRole()).isEqualTo(ParticipantRole.OWNER);
  }

  @Test
  void firstJoinAfterEveryoneLeavesBecomesOwner() {
    var owner = users.save(new User("빈팀생성자", 1003));
    var newcomer = users.save(new User("새참여자", 1004));
    var exploration = exploration(owner.getId(), ExplorationStatus.BEFORE);
    var originalOwner = participant(exploration.getId(), owner.getId(), ParticipantRole.OWNER);
    service.leave(exploration.getId(), owner.getId());
    assertThat(service.join(exploration.getCourseId(), newcomer.getId()).role()).isEqualTo("OWNER");
    assertThat(originalOwner.getRole()).isEqualTo(ParticipantRole.MEMBER);
    assertThat(service.join(exploration.getCourseId(), owner.getId()).role()).isEqualTo("MEMBER");
  }

  private Exploration exploration(Long ownerId, ExplorationStatus status) {
    var course =
        courses.save(
            Course.builder()
                .ownerUserId(ownerId)
                .title("이탈 검증 코스")
                .status(CourseStatus.CONFIRMED)
                .travelSchedule(TravelSchedule.DAY_TRIP)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .shareExpiresAt(LocalDateTime.now().plusDays(3))
                .confirmedAt(LocalDateTime.now())
                .build());
    return explorations.save(Exploration.builder().courseId(course.getId()).status(status).build());
  }

  private ExplorationParticipant participant(
      Long explorationId, Long userId, ParticipantRole role) {
    return participants.saveAndFlush(
        ExplorationParticipant.builder()
            .explorationId(explorationId)
            .userId(userId)
            .role(role)
            .status(ParticipantStatus.ACTIVE)
            .displayName("참여자")
            .joinedAt(LocalDateTime.now())
            .locationSharingEnabled(true)
            .build());
  }
}
