package com.example.beyond_may_be.course.repository;

import com.example.beyond_may_be.course.domain.CoursePlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoursePlaceRepository extends JpaRepository<CoursePlace, Long> {
  Optional<CoursePlace> findByCourseIdAndPlaceId(Long courseId, Long placeId);

  long countByCourseId(Long courseId);

  List<CoursePlace> findByCourseIdOrderByDayNumberAscVisitOrderAsc(Long courseId);
}
