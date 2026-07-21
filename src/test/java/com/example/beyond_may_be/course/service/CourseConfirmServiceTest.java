package com.example.beyond_may_be.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CourseConfirmServiceTest {

  @Test
  void returnsTemporaryConfirmedCourse() {
    CourseService service = new CourseService();

    ConfirmResponse response = service.confirmCourse("course_01J", new ConfirmRequest(true));

    assertEquals("course_01J", response.courseId());
    assertEquals("CONFIRMED", response.status());
    assertEquals("sess_temporary", response.ownerSessionId());
    assertEquals("share_temporary", response.share().shareId());
    assertEquals(5, response.share().maxMemberCount());
    assertEquals(OffsetDateTime.parse("2026-06-30T09:20:00+09:00"), response.confirmedAt());
  }
}
