package com.example.beyond_may_be.travelLog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateResponse;
import com.example.beyond_may_be.travelLog.service.TravelLogService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisitLogCreationControllerTest {

  @Test
  void delegatesVisitLogCreationToService() {
    TravelLogService travelLogService = mock(TravelLogService.class);
    VisitLogController controller = new VisitLogController(travelLogService);
    CreateRequest request =
        new CreateRequest(
            "course_01J",
            "place_001",
            "visit_01J",
            "양림동 펭귄마을",
            "골목 사이사이 벽화가 인상적이었어요.",
            List.of("https://cdn.example.com/uploads/photo_001.jpg"));
    CreateResponse expectedResponse =
        new CreateResponse(
            "vlog_01J",
            "course_01J",
            "place_001",
            "양림동 펭귄마을",
            "골목 사이사이 벽화가 인상적이었어요.",
            List.of("https://cdn.example.com/uploads/photo_001.jpg"),
            OffsetDateTime.parse("2026-06-30T12:43:10+09:00"));
    when(travelLogService.createVisitLog(request)).thenReturn(expectedResponse);

    ApiResponse<CreateResponse> response = controller.createVisitLog(request);

    assertEquals(expectedResponse, response.getData());
    verify(travelLogService).createVisitLog(request);
  }
}
