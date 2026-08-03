package com.example.beyond_may_be.recommendation.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "recommendation_sets",
    uniqueConstraints = {@UniqueConstraint(columnNames = "user_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationSet extends BaseEntity {

  @Id
  @Column(name = "recommendation_set_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "travel_schedule", nullable = false)
  private TravelSchedule travelSchedule;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "recommended_place_ids", nullable = false, columnDefinition = "jsonb")
  private List<Long> recommendedPlaceIds;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "liked_place_ids", nullable = false, columnDefinition = "jsonb")
  private List<Long> likedPlaceIds;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "disliked_place_ids", nullable = false, columnDefinition = "jsonb")
  private List<Long> dislikedPlaceIds;

  @Builder
  public RecommendationSet(
      Long userId,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      List<Long> recommendedPlaceIds,
      List<Long> likedPlaceIds,
      List<Long> dislikedPlaceIds) {
    this.userId = userId;
    this.travelSchedule = travelSchedule;
    this.startDate = startDate;
    this.endDate = endDate;
    this.recommendedPlaceIds = toMutableList(recommendedPlaceIds);
    this.likedPlaceIds = toMutableList(likedPlaceIds);
    this.dislikedPlaceIds = toMutableList(dislikedPlaceIds);
  }

  private static List<Long> toMutableList(List<Long> values) {
    return values == null ? new ArrayList<>() : new ArrayList<>(values);
  }
}
