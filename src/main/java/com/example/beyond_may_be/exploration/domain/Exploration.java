package com.example.beyond_may_be.exploration.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "explorations",
    uniqueConstraints = {@UniqueConstraint(columnNames = "course_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exploration extends BaseEntity {

  @Id
  @Column(name = "exploration_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "course_id", nullable = false)
  private Long courseId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ExplorationStatus status;

  @Column(name = "started_by_participant_id")
  private Long startedByParticipantId;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Builder
  public Exploration(
      Long courseId,
      ExplorationStatus status,
      Long startedByParticipantId,
      LocalDateTime startedAt,
      LocalDateTime completedAt) {
    this.courseId = courseId;
    this.status = status;
    this.startedByParticipantId = startedByParticipantId;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
  }
}
