package com.example.beyond_may_be.course.service;

import com.example.beyond_may_be.course.converter.CourseConverter;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.AiGenerationResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CompleteResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.ConfirmResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.CourseResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.TripScheduleResponse;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceRequest;
import com.example.beyond_may_be.course.dto.CourseDtos.VisitPlaceResponse;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class CourseService {
  public TripScheduleResponse createTripSchedule(TripScheduleRequest request) {
    return CourseConverter.toTripScheduleResponse(
        "schedule_temporary",
        request.durationType(),
        3,
        10,
        10,
        OffsetDateTime.parse("2026-06-30T09:00:00+09:00"));
  }

  public AiGenerationResponse requestAiGeneration(AiGenerationRequest request) {
    return CourseConverter.toAiGenerationResponse(request.selectedPlaceIds().size());
  }

  public ConfirmResponse confirmCourse(String courseId, ConfirmRequest request) {
    return CourseConverter.toConfirmResponse(courseId);
  }

  public CourseResponse getCourse(String courseId, String include) {
    return CourseConverter.toCourseResponse(courseId);
  }

  public CompleteResponse completeCourse(Long scheduleId) {
    return CourseConverter.toCompleteResponse(scheduleId);
  }

  public VisitPlaceResponse visitPlace(
      String scheduleId, String placeId, VisitPlaceRequest request) {
    return CourseConverter.toVisitPlaceResponse(placeId);
  }
}
