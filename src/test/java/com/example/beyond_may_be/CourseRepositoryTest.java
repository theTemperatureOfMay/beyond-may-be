package com.example.beyond_may_be;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.beyond_may_be.common.config.JpaAuditingConfig;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.course.repository.CourseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class CourseRepositoryTest {
  @Autowired CourseRepository courses;
  @Autowired TestEntityManager entityManager;

  @Test
  void ownerListSortsByModificationTimeThenIdAndIncludesAllStatuses() {
    Course older = save(81L, CourseStatus.DRAFT);
    Course recent = save(81L, CourseStatus.DRAFT);
    Course tied = save(81L, CourseStatus.CONFIRMED);
    save(82L, CourseStatus.DRAFT);
    LocalDateTime time = LocalDateTime.of(2026, 9, 19, 10, 0);
    setTime(older, time.minusDays(1));
    setTime(recent, time);
    setTime(tied, time);
    entityManager.clear();
    assertThat(courses.findByOwnerUserIdOrderByUpdatedAtDescIdDesc(81L))
        .extracting(Course::getId)
        .containsExactly(tied.getId(), recent.getId(), older.getId());
    assertThat(courses.findByOwnerUserIdOrderByUpdatedAtDescIdDesc(83L)).isEmpty();
  }

  private Course save(long owner, CourseStatus status) {
    return courses.saveAndFlush(
        Course.builder()
            .ownerUserId(owner)
            .title("여행")
            .status(status)
            .travelSchedule(TravelSchedule.DAY_TRIP)
            .startDate(LocalDate.of(2026, 9, 20))
            .endDate(LocalDate.of(2026, 9, 20))
            .startTime(LocalTime.of(9, 0))
            .build());
  }

  private void setTime(Course course, LocalDateTime time) {
    entityManager
        .getEntityManager()
        .createQuery("update Course c set c.updatedAt = :time where c.id = :id")
        .setParameter("time", time)
        .setParameter("id", course.getId())
        .executeUpdate();
  }
}
