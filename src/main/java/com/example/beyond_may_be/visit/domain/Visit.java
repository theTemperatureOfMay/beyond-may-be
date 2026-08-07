package com.example.beyond_may_be.visit.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "visits",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"participant_id", "place_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Visit extends BaseEntity {

  @Id
  @Column(name = "visit_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "place_id", nullable = false)
  private Long placeId;

  @Column(name = "course_place_id")
  private Long coursePlaceId;

  @Column(name = "participant_id", nullable = false)
  private Long participantId;

  @Column(name = "visited_at", nullable = false)
  private LocalDateTime visitedAt;

  @Builder
  public Visit(Long participantId, Long placeId, Long coursePlaceId, LocalDateTime visitedAt) {
    this.participantId = participantId;
    this.placeId = placeId;
    this.coursePlaceId = coursePlaceId;
    this.visitedAt = visitedAt;
  }
}
