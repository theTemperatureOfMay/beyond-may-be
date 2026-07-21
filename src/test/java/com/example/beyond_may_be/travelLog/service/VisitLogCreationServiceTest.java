package com.example.beyond_may_be.travelLog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisitLogCreationServiceTest {

  @Test
  void returnsTemporaryCreatedVisitLog() {
    TravelLogService service = new TravelLogService();
    CreateRequest request =
        new CreateRequest(
            "course_01J",
            "place_001",
            "visit_01J",
            "양림동 펭귄마을",
            "골목 사이사이 벽화가 인상적이었어요.",
            List.of("https://cdn.example.com/uploads/photo_001.jpg"));

    CreateResponse response = service.createVisitLog(request);

    assertEquals("vlog_temporary", response.visitLogId());
    assertEquals(request.courseId(), response.courseId());
    assertEquals(request.placeId(), response.placeId());
    assertEquals(request.placeName(), response.placeName());
    assertEquals(request.memo(), response.memo());
    assertEquals(request.photoUrls(), response.photoUrls());
    assertEquals(OffsetDateTime.parse("2026-06-30T12:43:10+09:00"), response.createdAt());
  }
}
