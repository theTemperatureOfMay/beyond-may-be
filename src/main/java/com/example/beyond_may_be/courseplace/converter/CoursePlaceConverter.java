package com.example.beyond_may_be.courseplace.converter;

import com.example.beyond_may_be.courseplace.dto.CoursePlaceDtos.VisitResponse;

public final class CoursePlaceConverter {
  private CoursePlaceConverter() {}

  public static VisitResponse toVisitResponse(String placeId) {
    return new VisitResponse(placeId, true, "1970-01-01T00:00:00", "temporary-user-id");
  }
}
