package com.example.beyond_may_be.course.repository;

import com.example.beyond_may_be.course.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {}
