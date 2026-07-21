package com.example.beyond_may_be.courseplace.dto;

public final class CoursePlaceDtos {
  private CoursePlaceDtos() {}

  public record VisitRequest(double latitude, double longitude, boolean hasGpsPermission) {}

  public record VisitResponse(
      String placeId, boolean visited, String visitedAt, String visitedBy) {}
}
