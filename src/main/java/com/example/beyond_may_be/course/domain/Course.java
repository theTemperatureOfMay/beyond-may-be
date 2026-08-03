package com.example.beyond_may_be.course.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseEntity {

  @Id
  @Column(name = "course_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Column(nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CourseStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "travel_schedule", nullable = false)
  private TravelSchedule travelSchedule;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "share_expires_at")
  private LocalDateTime shareExpiresAt;

  @Column(name = "ai_revision_count", nullable = false)
  private int aiRevisionCount;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Builder
  public Course(
      Long ownerUserId,
      String title,
      CourseStatus status,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalDateTime shareExpiresAt,
      int aiRevisionCount,
      LocalDateTime confirmedAt) {
    this.ownerUserId = ownerUserId;
    this.title = title;
    this.status = status;
    this.travelSchedule = travelSchedule;
    this.startDate = startDate;
    this.endDate = endDate;
    this.startTime = startTime;
    this.shareExpiresAt = shareExpiresAt;
    this.aiRevisionCount = aiRevisionCount;
    this.confirmedAt = confirmedAt;
  }
}
