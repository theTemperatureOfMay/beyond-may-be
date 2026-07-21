package com.example.beyond_may_be.travelLog.service;

import com.example.beyond_may_be.travelLog.converter.TravelLogConverter;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.DetailResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListResponse;
import org.springframework.stereotype.Service;

@Service
public class TravelLogService {

  public ListResponse getVisitLogs(ListRequest request) {
    return TravelLogConverter.toListResponse();
  }

  public CreateResponse createVisitLog(CreateRequest request) {
    return TravelLogConverter.toCreateResponse(request);
  }

  public DetailResponse getVisitLog(String visitLogId) {
    return TravelLogConverter.toDetailResponse(visitLogId);
  }
}
