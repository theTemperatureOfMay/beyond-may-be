package com.example.beyond_may_be.travelLog.controller;

import com.example.beyond_may_be.apiPayload.ApiResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.DetailResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListResponse;
import com.example.beyond_may_be.travelLog.service.TravelLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/visit-logs")
public class VisitLogController {
  private final TravelLogService travelLogService;

  @GetMapping
  public ApiResponse<ListResponse> getVisitLogs(
      @RequestParam(required = false) String courseId,
      @RequestParam(defaultValue = "TEAM") String scope,
      @RequestParam(required = false) String placeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") Integer limit) {
    ListRequest request = new ListRequest(courseId, scope, placeId, cursor, limit);
    return ApiResponse.onSuccess(travelLogService.getVisitLogs(request));
  }

  @PostMapping
  public ApiResponse<CreateResponse> createVisitLog(@RequestBody CreateRequest request) {
    return ApiResponse.onSuccess(travelLogService.createVisitLog(request));
  }

  @GetMapping("/{visitLogId}")
  public ApiResponse<DetailResponse> getVisitLog(@PathVariable String visitLogId) {
    return ApiResponse.onSuccess(travelLogService.getVisitLog(visitLogId));
  }
}
