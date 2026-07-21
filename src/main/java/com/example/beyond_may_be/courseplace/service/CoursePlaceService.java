package com.example.beyond_may_be.courseplace.service;

import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitRequest;
import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;

public interface CoursePlaceService {
  VisitResponse visitPlace(Long scheduleId, Long placeId, VisitRequest request);
}
