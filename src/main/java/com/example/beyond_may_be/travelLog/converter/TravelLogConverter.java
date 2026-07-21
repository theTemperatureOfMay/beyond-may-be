package com.example.beyond_may_be.travelLog.converter;

import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateRequest;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.CreateResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.DetailResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.ListResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.PageInfoResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.VisitLogAuthorResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.VisitLogCourseResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.VisitLogDetailPlaceResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.VisitLogItemResponse;
import com.example.beyond_may_be.travelLog.dto.TravelLogDtos.VisitLogPlaceResponse;
import java.time.OffsetDateTime;
import java.util.List;

public final class TravelLogConverter {
  private TravelLogConverter() {}

  public static ListResponse toListResponse() {
    VisitLogItemResponse item =
        new VisitLogItemResponse(
            "vlog_temporary",
            "course_01J",
            new VisitLogPlaceResponse(
                "place_001", "양림동 펭귄마을", "근대골목", "https://cdn.example.com/places/place_001.jpg"),
            new VisitLogAuthorResponse("sess_temporary", "김감자감자", "OWNER"),
            "GPS",
            "2026-06-30T12:40:00+09:00",
            "골목 분위기가 좋았어요.",
            2,
            "2026-06-30T12:43:10+09:00");
    return new ListResponse(List.of(item), new PageInfoResponse("eyJ2aXNpdGVkQXQiOiIyMDI2", true));
  }

  public static CreateResponse toCreateResponse(CreateRequest request) {
    return new CreateResponse(
        "vlog_temporary",
        request.courseId(),
        request.placeId(),
        request.placeName(),
        request.memo(),
        request.photoUrls(),
        OffsetDateTime.parse("2026-06-30T12:43:10+09:00"));
  }

  public static DetailResponse toDetailResponse(String visitLogId) {
    return new DetailResponse(
        visitLogId,
        new VisitLogCourseResponse("course_01J", "하루치 광주"),
        new VisitLogDetailPlaceResponse(
            "place_001",
            "양림동 펭귄마을",
            "광주광역시 남구 천변좌로446번길 7",
            "근대골목",
            "https://cdn.example.com/places/place_001.jpg"),
        new VisitLogAuthorResponse("sess_temporary", "김감자감자", "OWNER"),
        "골목 사이사이 벽화가 인상적이었어요.",
        List.of(
            "https://cdn.example.com/uploads/photo_001.jpg",
            "https://cdn.example.com/uploads/photo_002.jpg"),
        "2026-06-30T12:43:10+09:00",
        "2026-06-30T12:43:10+09:00");
  }
}
