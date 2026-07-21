package com.example.beyond_may_be.courseplace.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitRequest;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;
import com.example.beyond_may_be.courseplace.service.CoursePlaceService;
import org.junit.jupiter.api.Test;

class CoursePlaceControllerTest {

  @Test
  void delegatesPlaceVisitToService() {
    CoursePlaceService coursePlaceService = mock(CoursePlaceService.class);
    CoursePlaceController controller = new CoursePlaceController(coursePlaceService);
    VisitRequest request = new VisitRequest(35.1595, 126.8526, true);
    VisitResponse expectedResponse =
        new VisitResponse("place_001", true, "1970-01-01T00:00:00", "temporary-user-id");

    when(coursePlaceService.visitPlace("schedule_01J", "place_001", request))
        .thenReturn(expectedResponse);

    ApiResponse<VisitResponse> response =
        controller.visitPlace("schedule_01J", "place_001", request);

    assertEquals(expectedResponse, response.getData());
    verify(coursePlaceService).visitPlace("schedule_01J", "place_001", request);
  }
}
