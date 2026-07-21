package com.example.beyond_may_be.travelLog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.DetailResponse;
import org.junit.jupiter.api.Test;

class TravelLogDetailServiceTest {

  @Test
  void returnsTemporaryVisitLogDetail() {
    TravelLogService service = new TravelLogService();

    DetailResponse response = service.getVisitLog("vlog_01J");

    assertEquals("vlog_01J", response.visitLogId());
    assertEquals("course_01J", response.course().courseId());
    assertEquals("place_001", response.place().placeId());
    assertEquals("OWNER", response.author().role());
    assertEquals(2, response.photoUrls().size());
    assertEquals("2026-06-30T12:43:10+09:00", response.updatedAt());
  }
}
