package com.example.beyond_may_be.travelLog.dto;

import java.time.OffsetDateTime;
import java.util.List;

public final class TravelLogDtos {

  private TravelLogDtos() {}

  public record ListRequest(
      String courseId, String scope, String placeId, String cursor, Integer limit) {}

  public record ListResponse(List<VisitLogItemResponse> items, PageInfoResponse pageInfo) {}

  public record VisitLogItemResponse(
      String visitLogId,
      String courseId,
      VisitLogPlaceResponse place,
      VisitLogAuthorResponse author,
      String verificationType,
      String visitedAt,
      String memoPreview,
      int photoCount,
      String createdAt) {}

  public record VisitLogPlaceResponse(
      String placeId, String name, String category, String thumbnailUrl) {}

  public record VisitLogAuthorResponse(String sessionId, String nickname, String role) {}

  public record PageInfoResponse(String nextCursor, boolean hasNext) {}

  public record CreateRequest(
      String courseId,
      String placeId,
      String visitId,
      String placeName,
      String memo,
      List<String> photoUrls) {}

  public record CreateResponse(
      String visitLogId,
      String courseId,
      String placeId,
      String placeName,
      String memo,
      List<String> photoUrls,
      OffsetDateTime createdAt) {}

  public record DetailResponse(
      String visitLogId,
      VisitLogCourseResponse course,
      VisitLogDetailPlaceResponse place,
      VisitLogAuthorResponse author,
      String memo,
      List<String> photoUrls,
      String createdAt,
      String updatedAt) {}

  public record VisitLogCourseResponse(String courseId, String title) {}

  public record VisitLogDetailPlaceResponse(
      String placeId, String name, String address, String category, String thumbnailUrl) {}
}
