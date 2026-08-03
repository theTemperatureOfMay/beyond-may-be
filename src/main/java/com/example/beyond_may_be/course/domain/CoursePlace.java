package com.example.beyond_may_be.course.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "course_places",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"course_id", "place_id"}),
      @UniqueConstraint(columnNames = {"course_id", "day_number", "visit_order"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePlace extends BaseEntity {

  @Id
  @Column(name = "course_place_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "course_id", nullable = false)
  private Long courseId;

  @Column(name = "place_id", nullable = false)
  private Long placeId;

  @Column(name = "day_number", nullable = false)
  private Integer dayNumber;

  @Column(name = "visit_order", nullable = false)
  private Integer visitOrder;

  @Column(name = "estimated_stay_minutes", nullable = false)
  private Integer estimatedStayMinutes;

  @Column(name = "travel_mode_from_previous")
  private String travelModeFromPrevious;

  @Builder
  public CoursePlace(
      Long courseId,
      Long placeId,
      Integer dayNumber,
      Integer visitOrder,
      Integer estimatedStayMinutes,
      String travelModeFromPrevious) {
    this.courseId = courseId;
    this.placeId = placeId;
    this.dayNumber = dayNumber;
    this.visitOrder = visitOrder;
    this.estimatedStayMinutes = estimatedStayMinutes;
    this.travelModeFromPrevious = travelModeFromPrevious;
  }
}
