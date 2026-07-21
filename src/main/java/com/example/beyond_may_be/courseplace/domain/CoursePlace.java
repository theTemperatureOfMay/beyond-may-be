package com.example.beyond_may_be.courseplace.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.course.domain.Course;
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
@Table(name = "course_places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePlace extends BaseEntity {

  @Id
  @Column(name = "place_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "place_img")
  private String placeImg;

  @Column(name = "place_intro", length = 200)
  private String placenItro;

  @Column(name = "place_priority")
  private Integer placePriority;

  @Column(name = "place_status")
  private Integer placeStatus;

  @Column(name = "time_taken")
  private Integer timeTaken;

  @Column(name = "lat", nullable = false)
  private Integer latitude;

  @Column(name = "long", nullable = false)
  private Integer longitude;

  @Column(nullable = false)
  private String address;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;
}
