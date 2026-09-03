package com.example.beyond_may_be.exploration.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.course.repository.CourseRepository;
import com.example.beyond_may_be.exploration.converter.ExplorationConverter;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import com.example.beyond_may_be.visit.domain.Visit;
import com.example.beyond_may_be.visit.repository.VisitRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExplorationService {

  private static final Comparator<ExplorationParticipant> PARTICIPANT_ORDER =
      Comparator.comparingInt(
              (ExplorationParticipant participant) ->
                  participant.getRole() == ParticipantRole.OWNER ? 0 : 1)
          .thenComparing(ExplorationParticipant::getJoinedAt);

  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final CourseRepository courseRepository;
  private final CoursePlaceRepository coursePlaceRepository;
  private final PlaceRepository placeRepository;
  private final UserRepository userRepository;
  private final VisitRepository visitRepository;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional(readOnly = true)
  public ExplorationDtos.ExplorationsResponse getExplorations(
      Long userId, ExplorationStatus status) {
    if (status == null || status == ExplorationStatus.BEFORE) {
      throw new ExplorationHandler(ErrorStatus._BAD_REQUEST);
    }

    List<Exploration> explorations =
        explorationRepository.findAllByParticipantUserIdAndStatus(userId, status);
    // ponytail: 과거 LEFT 탐험과 새 ONGOING이 겹치면 500으로 드러낸다. 이탈 API 도입 시 카드 우선순위를 계약한다.
    if (status == ExplorationStatus.ONGOING && explorations.size() > 1) {
      throw new IllegalStateException("진행 중 탐험은 최대 1개여야 합니다.");
    }
    if (explorations.isEmpty()) {
      return ExplorationConverter.toExplorationsResponse(status, List.of());
    }

    List<Long> explorationIds = explorations.stream().map(Exploration::getId).toList();
    List<Long> courseIds = explorations.stream().map(Exploration::getCourseId).toList();
    Map<Long, Exploration> explorationsById =
        explorations.stream().collect(Collectors.toMap(Exploration::getId, Function.identity()));
    Map<Long, Course> coursesById =
        courseRepository.findAllById(courseIds).stream()
            .collect(Collectors.toMap(Course::getId, Function.identity()));

    List<ExplorationParticipant> participants =
        explorationParticipantRepository.findByExplorationIdIn(explorationIds);
    Map<Long, List<ExplorationParticipant>> participantsByExplorationId =
        participants.stream()
            .collect(Collectors.groupingBy(ExplorationParticipant::getExplorationId));
    Map<Long, Long> explorationIdByParticipantId =
        participants.stream()
            .collect(
                Collectors.toMap(
                    ExplorationParticipant::getId, ExplorationParticipant::getExplorationId));

    List<CoursePlace> coursePlaces =
        coursePlaceRepository.findByCourseIdInOrderByDayNumberAscVisitOrderAsc(courseIds);
    Map<Long, List<CoursePlace>> coursePlacesByCourseId =
        coursePlaces.stream().collect(Collectors.groupingBy(CoursePlace::getCourseId));
    Map<Long, Set<Long>> coursePlaceIdsByCourseId =
        coursePlaces.stream()
            .collect(
                Collectors.groupingBy(
                    CoursePlace::getCourseId,
                    Collectors.mapping(CoursePlace::getId, Collectors.toSet())));
    Map<Long, Place> placesById =
        placeRepository
            .findAllById(coursePlaces.stream().map(CoursePlace::getPlaceId).toList())
            .stream()
            .collect(Collectors.toMap(Place::getId, Function.identity()));

    Map<Long, Set<Long>> completedCoursePlaceIdsByExplorationId = new HashMap<>();
    for (Visit visit :
        visitRepository.findByParticipantIdIn(
            participants.stream().map(ExplorationParticipant::getId).toList())) {
      Long explorationId = explorationIdByParticipantId.get(visit.getParticipantId());
      Exploration exploration = explorationsById.get(explorationId);
      if (exploration != null
          && visit.getCoursePlaceId() != null
          && coursePlaceIdsByCourseId
              .getOrDefault(exploration.getCourseId(), Set.of())
              .contains(visit.getCoursePlaceId())) {
        completedCoursePlaceIdsByExplorationId
            .computeIfAbsent(explorationId, ignored -> new HashSet<>())
            .add(visit.getCoursePlaceId());
      }
    }

    List<ExplorationDtos.ExplorationSummaryResponse> responses =
        explorations.stream()
            .map(
                exploration -> {
                  Course course =
                      Objects.requireNonNull(
                          coursesById.get(exploration.getCourseId()), "탐험의 코스를 찾을 수 없습니다.");
                  List<ExplorationParticipant> visibleParticipants =
                      participantsByExplorationId
                          .getOrDefault(exploration.getId(), List.of())
                          .stream()
                          .filter(participant -> participant.getStatus() != ParticipantStatus.LEFT)
                          .sorted(PARTICIPANT_ORDER)
                          .toList();
                  List<CoursePlace> explorationCoursePlaces =
                      coursePlacesByCourseId.getOrDefault(exploration.getCourseId(), List.of());
                  String representativeImageUrl =
                      explorationCoursePlaces.stream()
                          .map(CoursePlace::getPlaceId)
                          .map(placesById::get)
                          .filter(Objects::nonNull)
                          .map(Place::getThumbnailUrl)
                          .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                          .findFirst()
                          .orElse(null);
                  long completedCoursePlaceCount =
                      completedCoursePlaceIdsByExplorationId
                          .getOrDefault(exploration.getId(), Set.of())
                          .size();
                  return ExplorationConverter.toExplorationSummaryResponse(
                      exploration,
                      course,
                      representativeImageUrl,
                      visibleParticipants,
                      completedCoursePlaceCount,
                      explorationCoursePlaces.size());
                })
            .toList();
    return ExplorationConverter.toExplorationsResponse(status, responses);
  }

  public ExplorationDtos.JoinResponse join(Long courseId, Long userId) {
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

    Exploration exploration =
        explorationRepository
            .findByCourseIdForUpdate(courseId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    if (exploration.getStatus() == ExplorationStatus.COMPLETED) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
    }

    var existing =
        explorationParticipantRepository.findByExplorationIdAndUserId(exploration.getId(), userId);
    if (existing.isPresent() && existing.get().getStatus() != ParticipantStatus.LEFT) {
      return ExplorationConverter.toJoinResponse(existing.get(), true);
    }

    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    if (explorationParticipantRepository.existsActiveParticipation(userId)) {
      throw new ExplorationHandler(ErrorStatus.DUPLICATE_ACTIVE_PARTICIPATION);
    }
    if (existing.isPresent()) {
      ExplorationParticipant participant = existing.get();
      participant.reactivate();
      return ExplorationConverter.toJoinResponse(participant, true);
    }

    List<ExplorationParticipant> allParticipants =
        explorationParticipantRepository.findByExplorationId(exploration.getId());
    String displayName = resolveDisplayName(allParticipants, user.getNickname());

    ExplorationParticipant participant =
        explorationParticipantRepository.save(
            ExplorationParticipant.builder()
                .explorationId(exploration.getId())
                .userId(userId)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.ACTIVE)
                .displayName(displayName)
                .locationSharingEnabled(false)
                .joinedAt(LocalDateTime.now())
                .build());
    ExplorationDtos.JoinResponse response = ExplorationConverter.toJoinResponse(participant, false);
    int participantCount =
        Math.toIntExact(
            allParticipants.stream()
                    .filter(candidate -> candidate.getStatus() != ParticipantStatus.LEFT)
                    .count()
                + 1);
    applicationEventPublisher.publishEvent(
        ExplorationConverter.toParticipantJoinedEvent(
            UUID.randomUUID(), response, participantCount));
    return response;
  }

  public ExplorationDtos.StartResponse start(Long explorationId, Long userId) {
    Exploration exploration =
        explorationRepository
            .findById(explorationId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    ExplorationParticipant participant =
        explorationParticipantRepository
            .findByExplorationIdAndUserId(explorationId, userId)
            .filter(candidate -> candidate.getStatus() == ParticipantStatus.ACTIVE)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus._FORBIDDEN));
    LocalDateTime startedAt = LocalDateTime.now();
    if (explorationRepository.startIfBefore(explorationId, participant.getId(), startedAt) == 0) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_START_CONFLICT);
    }
    ExplorationDtos.StartResponse response =
        ExplorationConverter.toStartResponse(exploration, participant, startedAt);
    applicationEventPublisher.publishEvent(
        ExplorationConverter.toExplorationStartedEvent(UUID.randomUUID(), response));
    return response;
  }

  @Transactional(readOnly = true)
  public ExplorationDtos.ParticipantsResponse getParticipants(Long explorationId, Long userId) {
    explorationRepository
        .findById(explorationId)
        .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    ExplorationParticipant currentParticipant =
        explorationParticipantRepository
            .findByExplorationIdAndUserId(explorationId, userId)
            .filter(participant -> participant.getStatus() != ParticipantStatus.LEFT)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus._FORBIDDEN));
    List<ExplorationParticipant> participants =
        explorationParticipantRepository.findByExplorationId(explorationId).stream()
            .filter(participant -> participant.getStatus() != ParticipantStatus.LEFT)
            .sorted(PARTICIPANT_ORDER)
            .toList();
    List<Long> participantIds = participants.stream().map(ExplorationParticipant::getId).toList();
    Map<Long, Long> visitCounts =
        visitRepository.countByParticipantIds(participantIds).stream()
            .collect(
                Collectors.toMap(
                    VisitRepository.ParticipantVisitCount::getParticipantId,
                    VisitRepository.ParticipantVisitCount::getVisitCount));
    return ExplorationConverter.toParticipantsResponse(
        explorationId, participants, visitCounts, currentParticipant.getId());
  }

  @Transactional(readOnly = true)
  public ExplorationDtos.DetailResponse getDetail(Long explorationId, Long userId) {
    Exploration exploration =
        explorationRepository
            .findById(explorationId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    ExplorationParticipant currentParticipant =
        explorationParticipantRepository
            .findByExplorationIdAndUserId(explorationId, userId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus._FORBIDDEN));
    List<ExplorationParticipant> participants =
        explorationParticipantRepository.findByExplorationId(explorationId);
    List<CoursePlace> coursePlaces =
        coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(
            exploration.getCourseId());

    // ponytail: 탐험당 방문 수가 작다는 MVP 전제에서 한 번 조회해 집계한다. 커지면 DB 집계로 교체한다.
    List<Visit> visits =
        visitRepository.findByParticipantIdIn(
            participants.stream().map(ExplorationParticipant::getId).toList());
    Set<Long> coursePlaceIds =
        coursePlaces.stream().map(CoursePlace::getId).collect(Collectors.toSet());
    long participantCount =
        participants.stream()
            .filter(participant -> participant.getStatus() != ParticipantStatus.LEFT)
            .count();
    long teamVisitedPlaceCount = visits.stream().map(Visit::getPlaceId).distinct().count();
    long completedCoursePlaceCount =
        visits.stream()
            .map(Visit::getCoursePlaceId)
            .filter(coursePlaceIds::contains)
            .distinct()
            .count();
    long totalCoursePlaceCount = coursePlaces.size();
    int completionRate =
        totalCoursePlaceCount == 0
            ? 0
            : (int) (completedCoursePlaceCount * 100 / totalCoursePlaceCount);
    boolean canStart =
        currentParticipant.getStatus() == ParticipantStatus.ACTIVE
            && exploration.getStatus() == ExplorationStatus.BEFORE;
    boolean canCompleteEarly =
        currentParticipant.getRole() == ParticipantRole.OWNER
            && currentParticipant.getStatus() == ParticipantStatus.ACTIVE
            && exploration.getStatus() == ExplorationStatus.ONGOING;

    return ExplorationConverter.toDetailResponse(
        exploration,
        currentParticipant,
        participantCount,
        teamVisitedPlaceCount,
        completedCoursePlaceCount,
        totalCoursePlaceCount,
        completionRate,
        canStart,
        canCompleteEarly);
  }

  public ExplorationDtos.LocationSharingResponse updateLocationSharing(
      Long explorationId, Long userId, boolean enabled) {
    Exploration exploration =
        explorationRepository
            .findById(explorationId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    ExplorationParticipant participant =
        explorationParticipantRepository
            .findByExplorationIdAndUserIdForUpdate(explorationId, userId)
            .orElseThrow(
                () -> new ExplorationHandler(ErrorStatus.EXPLORATION_PARTICIPANT_NOT_FOUND));
    if (exploration.getStatus() == ExplorationStatus.COMPLETED) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_ALREADY_COMPLETED);
    }
    if (participant.getStatus() != ParticipantStatus.ACTIVE) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN);
    }
    if (!participant.changeLocationSharing(enabled)) {
      return ExplorationConverter.toLocationSharingResponse(participant);
    }
    explorationParticipantRepository.flush();
    ExplorationDtos.LocationSharingResponse response =
        ExplorationConverter.toLocationSharingResponse(participant);
    applicationEventPublisher.publishEvent(
        ExplorationConverter.toLocationSharingChangedEvent(UUID.randomUUID(), response));
    return response;
  }

  private String resolveDisplayName(List<ExplorationParticipant> allParticipants, String nickname) {
    Pattern suffixPattern = Pattern.compile(Pattern.quote(nickname) + " \\((\\d+)\\)$");
    long sameNameCount =
        allParticipants.stream()
            .filter(
                participant ->
                    participant.getDisplayName().equals(nickname)
                        || suffixPattern.matcher(participant.getDisplayName()).matches())
            .count();
    return sameNameCount == 0 ? nickname : nickname + " (" + (sameNameCount + 1) + ")";
  }
}
