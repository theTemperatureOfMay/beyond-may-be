package com.example.beyond_may_be.course.controller;

import static com.example.beyond_may_be.support.EndpointMappingAssertions.assertEndpoint;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

class CourseControllerStructureTest {

  @Test
  void declaresCourseEndpoints() {
    assertEndpoint(
        "com.example.beyond_may_be.course.controller.TripScheduleController",
        "createTripSchedule",
        PostMapping.class,
        "/api/v1/trip-schedules");
    assertEndpoint(
        "com.example.beyond_may_be.course.controller.CourseController",
        "requestAiGeneration",
        PostMapping.class,
        "/api/v1/courses/ai-generation");
    assertEndpoint(
        "com.example.beyond_may_be.course.controller.CourseController",
        "confirmCourse",
        PostMapping.class,
        "/api/v1/courses/{courseId}/confirm");
    assertEndpoint(
        "com.example.beyond_may_be.course.controller.CourseController",
        "getCourse",
        GetMapping.class,
        "/api/v1/courses/{courseId}");
    assertEndpoint(
        "com.example.beyond_may_be.course.controller.ScheduleController",
        "completeCourse",
        PatchMapping.class,
        "/api/schedules/{scheduleId}/course/complete");
  }
}
