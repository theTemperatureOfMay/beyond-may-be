package com.example.beyond_may_be.course.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.course.converter.CourseConverter;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.dto.CourseDtos;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.course.repository.CourseRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseService {

  private static final int SHARE_LINK_VALID_DAYS = 3;

  private final CourseRepository courseRepository;
  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final UserRepository userRepository;
  private final CoursePlaceRepository coursePlaceRepository;
  private final PlaceRepository placeRepository;

  public CourseDtos.ConfirmResponse confirm(Long courseId, Long userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_NOT_FOUND));

    if (!course.getOwnerUserId().equals(userId)) {
      throw new CourseHandler(ErrorStatus.COURSE_FORBIDDEN);
    }
    if (course.getStatus() != CourseStatus.DRAFT) {
      throw new CourseHandler(ErrorStatus.COURSE_ALREADY_CONFIRMED);
    }

    LocalDateTime now = LocalDateTime.now();
    course.confirm(now, now.plusDays(SHARE_LINK_VALID_DAYS));

    Exploration exploration =
        explorationRepository.save(
            Exploration.builder()
                .courseId(course.getId())
                .status(ExplorationStatus.BEFORE)
                .build());

    User owner =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

    explorationParticipantRepository.save(
        ExplorationParticipant.builder()
            .explorationId(exploration.getId())
            .userId(userId)
            .role(ParticipantRole.OWNER)
            .status(ParticipantStatus.ACTIVE)
            .displayName(owner.getNickname())
            .locationSharingEnabled(false)
            .joinedAt(now)
            .build());

    return CourseConverter.toConfirmResponse(course, exploration);
  }

  @Transactional(readOnly = true)
  public CourseDtos.CourseDetailResponse getCourseDetail(Long courseId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_NOT_FOUND));
    if (course.getStatus() != CourseStatus.CONFIRMED) {
      throw new CourseHandler(ErrorStatus.COURSE_NOT_FOUND);
    }
    if (course.getShareExpiresAt() == null
        || LocalDateTime.now().isAfter(course.getShareExpiresAt())) {
      throw new ExplorationHandler(ErrorStatus.SHARE_LINK_EXPIRED);
    }

    List<CoursePlace> coursePlaces =
        coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(courseId);
    List<Long> placeIds = coursePlaces.stream().map(CoursePlace::getPlaceId).toList();
    Map<Long, Place> placesById =
        placeRepository.findAllById(placeIds).stream()
            .collect(Collectors.toMap(Place::getId, Function.identity()));

    return CourseConverter.toCourseDetailResponse(course, coursePlaces, placesById);
  }
}
