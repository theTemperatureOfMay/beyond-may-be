package com.example.beyond_may_be.travelLog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.DetailResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.PageInfoResponse;
import com.example.beyond_may_be.travelLog.service.TravelLogService;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisitLogControllerTest {

  @Test
  void delegatesVisitLogQueryToService() {
    TravelLogService travelLogService = mock(TravelLogService.class);
    VisitLogController controller = new VisitLogController(travelLogService);
    ListRequest request = new ListRequest("course_01J", "TEAM", "place_001", "cursor_01", 20);
    ListResponse expectedResponse = new ListResponse(List.of(), new PageInfoResponse(null, false));

    when(travelLogService.getVisitLogs(request)).thenReturn(expectedResponse);

    ApiResponse<ListResponse> response =
        controller.getVisitLogs("course_01J", "TEAM", "place_001", "cursor_01", 20);

    assertEquals(expectedResponse, response.getData());
    verify(travelLogService).getVisitLogs(request);
  }

  @Test
  void delegatesVisitLogDetailQueryToService() {
    TravelLogService travelLogService = mock(TravelLogService.class);
    VisitLogController controller = new VisitLogController(travelLogService);
    DetailResponse expectedResponse =
        new DetailResponse(
            "vlog_01J",
            null,
            null,
            null,
            null,
            List.of(),
            "2026-06-30T12:43:10+09:00",
            "2026-06-30T12:43:10+09:00");

    when(travelLogService.getVisitLog("vlog_01J")).thenReturn(expectedResponse);

    ApiResponse<DetailResponse> response = controller.getVisitLog("vlog_01J");

    assertEquals(expectedResponse, response.getData());
    verify(travelLogService).getVisitLog("vlog_01J");
  }
}
