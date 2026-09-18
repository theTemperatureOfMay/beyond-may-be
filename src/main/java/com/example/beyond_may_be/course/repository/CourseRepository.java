package com.example.beyond_may_be.course.repository;

import com.example.beyond_may_be.course.domain.Course;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
  List<Course> findByOwnerUserIdOrderByUpdatedAtDescIdDesc(Long ownerUserId);
}
