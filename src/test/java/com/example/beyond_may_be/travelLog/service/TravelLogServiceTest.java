package com.example.beyond_may_be.travelLog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListResponse;
import org.junit.jupiter.api.Test;

class TravelLogServiceTest {

  @Test
  void returnsTemporaryVisitLogList() {
    TravelLogService service = new TravelLogService();
    ListRequest request = new ListRequest("course_01J", "TEAM", "place_001", null, 20);

    ListResponse response = service.getVisitLogs(request);

    assertEquals(1, response.items().size());
    assertEquals("vlog_temporary", response.items().getFirst().visitLogId());
    assertEquals("GPS", response.items().getFirst().verificationType());
    assertEquals("OWNER", response.items().getFirst().author().role());
    assertTrue(response.pageInfo().hasNext());
  }
}
