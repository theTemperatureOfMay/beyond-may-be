package com.example.beyond_may_be.coreplace.dto;

import java.util.List;

public final class CorePlaceDtos {

  private CorePlaceDtos() {}

  public record RecommendationsRequest(
      String scheduleId, String cursor, Integer limit, String excludePlaceIds) {}

  public record RecommendationsResponse(
      String scheduleId,
      String personalityType,
      String durationType,
      int minSelectablePlaceCount,
      int selectedPlaceCount,
      int recommendationBatchSize,
      List<RecommendationItemResponse> items,
      PageInfoResponse pageInfo) {}

  public record RecommendationItemResponse(
      String placeId,
      String name,
      String category,
      List<String> tags,
      String summary,
      String thumbnailUrl,
      String address,
      LocationResponse location,
      String recommendationReason) {}

  public record LocationResponse(double lat, double lng) {}

  public record PageInfoResponse(String nextCursor, boolean hasNext) {}

  public record SearchRequest(
      String courseId,
      String keyword,
      List<String> categories,
      SearchLocationRequest location,
      int radiusMeters,
      int limit) {}

  public record SearchLocationRequest(double lat, double lng) {}

  public record SearchResponse(List<SearchItemResponse> items) {}

  public record SearchItemResponse(
      String placeId,
      String name,
      String category,
      List<String> tags,
      String summary,
      String thumbnailUrl,
      String address,
      LocationResponse location,
      int distanceMeters,
      boolean alreadyInCourse) {}

  public record NearbyPlacesResponse(List<NearbyPlaceResponse> places) {}

  public record NearbyPlaceResponse(
      String placeId, String name, String category, int distanceMeters, String thumbnailUrl) {}
}
