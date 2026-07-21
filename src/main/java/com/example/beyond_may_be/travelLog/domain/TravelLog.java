package com.example.beyond_may_be.travelLog.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "travel_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelLog extends BaseEntity {

  @Id
  @Column(name = "log_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "log_img", nullable = false)
  private String logImg;

  @Column(name = "log_map", nullable = false)
  private String logMap;

  @Column(name = "is_planned", nullable = false)
  private Boolean isPlanned;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;
}
