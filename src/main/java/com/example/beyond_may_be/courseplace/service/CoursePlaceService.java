package com.example.beyond_may_be.courseplace.service;

import com.example.beyond_may_be.courseplace.converter.CoursePlaceConverter;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitRequest;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;
import org.springframework.stereotype.Service;

@Service
public class CoursePlaceService {
  public VisitResponse visitPlace(String scheduleId, String placeId, VisitRequest request) {
    return CoursePlaceConverter.toVisitResponse(placeId);
  }
}
