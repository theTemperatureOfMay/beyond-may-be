package com.example.beyond_may_be.course.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.CourseHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.RecommendationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.converter.CourseConverter;
import com.example.beyond_may_be.course.domain.Course;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.domain.enums.CourseStatus;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
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
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.recommendation.repository.RecommendationSetRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseService {

  private static final int SHARE_LINK_VALID_DAYS = 3;
  private static final String TRAVEL_MODE_WALK = "WALK";
  private static final int CHAT_MESSAGE_MAX_LENGTH = 150;
  private static final int MAX_AI_REVISIONS = 2;
  private static final int DEFAULT_STAY_MINUTES = 60;
  private static final LocalTime DEFAULT_START_TIME = LocalTime.of(7, 0);
  private static final String DEFAULT_TITLE = "광주 여행";

  private static final Map<TravelSchedule, Integer> MIN_PLACE_COUNT =
      new EnumMap<>(TravelSchedule.class);

  static {
    MIN_PLACE_COUNT.put(TravelSchedule.DAY_TRIP, 3);
    MIN_PLACE_COUNT.put(TravelSchedule.ONE_NIGHT_TWO_DAYS, 5);
    MIN_PLACE_COUNT.put(TravelSchedule.TWO_NIGHTS_THREE_DAYS, 7);
    MIN_PLACE_COUNT.put(TravelSchedule.CUSTOM, 20);
  }

  private final CourseRepository courseRepository;
  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final UserRepository userRepository;
  private final CoursePlaceRepository coursePlaceRepository;
  private final PlaceRepository placeRepository;
  private final GroqCourseChatClient groqCourseChatClient;
  private final GroqCourseOrderClient groqCourseOrderClient;
  private final RecommendationSetRepository recommendationSetRepository;
  private final TransactionTemplate transactionTemplate;

  private int dayCount(TravelSchedule travelSchedule, LocalDate startDate, LocalDate endDate) {
    return switch (travelSchedule) {
      case DAY_TRIP -> 1;
      case ONE_NIGHT_TWO_DAYS -> 2;
      case TWO_NIGHTS_THREE_DAYS -> 3;
      case CUSTOM -> (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    };
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CourseDtos.CourseDetailResponse generate(Long userId) {
    RecommendationSet recommendationSet =
        recommendationSetRepository
            .findByUserId(userId)
            .orElseThrow(() -> new RecommendationHandler(ErrorStatus.RECOMMENDATION_NOT_FOUND));
    List<Long> likedPlaceIds = List.copyOf(recommendationSet.getLikedPlaceIds());
    if (new LinkedHashSet<>(likedPlaceIds).size() != likedPlaceIds.size()) {
      throw new CourseHandler(ErrorStatus.COURSE_DUPLICATE_PLACE);
    }
    if (likedPlaceIds.size() < MIN_PLACE_COUNT.get(recommendationSet.getTravelSchedule())) {
      throw new CourseHandler(ErrorStatus.COURSE_INVALID_PLACE_COUNT);
    }

    Map<Long, Place> placesById =
        placeRepository.findAllById(likedPlaceIds).stream()
            .filter(Place::isActive)
            .collect(Collectors.toMap(Place::getId, Function.identity()));
    if (!placesById.keySet().containsAll(likedPlaceIds)) {
      throw new CourseHandler(ErrorStatus.COURSE_PLACE_NOT_FOUND);
    }
    List<Place> places = likedPlaceIds.stream().map(placesById::get).toList();
    int totalDays =
        dayCount(
            recommendationSet.getTravelSchedule(),
            recommendationSet.getStartDate(),
            recommendationSet.getEndDate());
    List<List<Long>> aiOrderedDays =
        groqCourseOrderClient.order(
            new GroqCourseOrderClient.OrderRequest(
                recommendationSet.getTravelSchedule(),
                recommendationSet.getStartDate(),
                recommendationSet.getEndDate(),
                totalDays,
                places));
    List<List<Long>> orderedDays =
        isValidGeneratedOrder(aiOrderedDays, totalDays, likedPlaceIds)
            ? aiOrderedDays
            : nearestNeighborOrder(places, totalDays);
    ensureGenerationRequestActive();

    return transactionTemplate.execute(
        status ->
            saveGeneratedCourse(
                userId,
                recommendationSet.getTravelSchedule(),
                recommendationSet.getStartDate(),
                recommendationSet.getEndDate(),
                likedPlaceIds,
                orderedDays));
  }

  private List<List<Long>> nearestNeighborOrder(List<Place> places, int totalDays) {
    List<Place> remaining = new ArrayList<>(places);
    List<Long> ordered = new ArrayList<>();
    Place current = remaining.remove(0);
    ordered.add(current.getId());
    while (!remaining.isEmpty()) {
      Place previous = current;
      current =
          remaining.stream()
              .min(
                  Comparator.comparingDouble((Place place) -> distance(previous, place))
                      .thenComparing(Place::getId))
              .orElseThrow();
      remaining.remove(current);
      ordered.add(current.getId());
    }
    ordered = arrangeMealSlots(ordered, places, totalDays);

    List<List<Long>> days = new ArrayList<>();
    int offset = 0;
    for (int day = 0; day < totalDays; day++) {
      int size = ordered.size() / totalDays + (day < ordered.size() % totalDays ? 1 : 0);
      days.add(List.copyOf(ordered.subList(offset, offset + size)));
      offset += size;
    }
    return days;
  }

  private List<Long> arrangeMealSlots(List<Long> nearestOrder, List<Place> places, int totalDays) {
    Set<Long> foodieIds =
        places.stream()
            .filter(place -> place.getTravelMbtiType() == TravelPreferenceType.FOODIE)
            .map(Place::getId)
            .collect(Collectors.toSet());
    List<Long> foodies = nearestOrder.stream().filter(foodieIds::contains).toList();
    if (foodies.isEmpty()) {
      return nearestOrder;
    }

    List<Integer> mealSlots = new ArrayList<>();
    int offset = 0;
    for (int day = 0; day < totalDays; day++) {
      int size = nearestOrder.size() / totalDays + (day < nearestOrder.size() % totalDays ? 1 : 0);
      if (size >= 2) {
        mealSlots.add(offset + size / 2);
      }
      if (size >= 3) {
        mealSlots.add(offset + size - 1);
      }
      offset += size;
    }
    Set<Integer> occupiedMealSlots =
        mealSlots.stream()
            .limit(foodies.size())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<Long> nonFoodies = nearestOrder.stream().filter(id -> !foodieIds.contains(id)).toList();
    List<Long> arranged = new ArrayList<>();
    int foodieIndex = 0;
    int nonFoodieIndex = 0;
    for (int index = 0; index < nearestOrder.size(); index++) {
      if (occupiedMealSlots.contains(index) || nonFoodieIndex >= nonFoodies.size()) {
        arranged.add(foodies.get(foodieIndex++));
      } else {
        arranged.add(nonFoodies.get(nonFoodieIndex++));
      }
    }
    return arranged;
  }

  private double distance(Place from, Place to) {
    return GeoDistanceCalculator.distanceMeters(
        from.getLatitude().doubleValue(),
        from.getLongitude().doubleValue(),
        to.getLatitude().doubleValue(),
        to.getLongitude().doubleValue());
  }

  private boolean isValidGeneratedOrder(
      List<List<Long>> orderedDays, int totalDays, List<Long> expectedPlaceIds) {
    if (orderedDays == null || orderedDays.size() != totalDays) {
      return false;
    }
    List<Long> orderedPlaceIds = new ArrayList<>();
    int minimumDaySize = Integer.MAX_VALUE;
    int maximumDaySize = 0;
    for (List<Long> day : orderedDays) {
      if (day == null || day.isEmpty()) {
        return false;
      }
      orderedPlaceIds.addAll(day);
      minimumDaySize = Math.min(minimumDaySize, day.size());
      maximumDaySize = Math.max(maximumDaySize, day.size());
    }
    return orderedPlaceIds.size() == expectedPlaceIds.size()
        && maximumDaySize - minimumDaySize <= 1
        && new HashSet<>(orderedPlaceIds).size() == orderedPlaceIds.size()
        && new HashSet<>(orderedPlaceIds).equals(new HashSet<>(expectedPlaceIds));
  }

  private CourseDtos.CourseDetailResponse saveGeneratedCourse(
      Long userId,
      TravelSchedule travelSchedule,
      LocalDate startDate,
      LocalDate endDate,
      List<Long> likedPlaceIds,
      List<List<Long>> orderedDays) {
    userRepository
        .findByIdForUpdate(userId)
        .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    RecommendationSet current =
        recommendationSetRepository
            .findByUserId(userId)
            .orElseThrow(
                () -> new RecommendationHandler(ErrorStatus.RECOMMENDATION_BATCH_CONFLICT));
    if (current.getTravelSchedule() != travelSchedule
        || !current.getStartDate().equals(startDate)
        || !current.getEndDate().equals(endDate)
        || !current.getLikedPlaceIds().equals(likedPlaceIds)) {
      throw new RecommendationHandler(ErrorStatus.RECOMMENDATION_BATCH_CONFLICT);
    }
    ensureGenerationRequestActive();

    Map<Long, Place> placesById =
        placeRepository.findAllById(likedPlaceIds).stream()
            .filter(Place::isActive)
            .collect(Collectors.toMap(Place::getId, Function.identity()));
    if (!placesById.keySet().containsAll(likedPlaceIds)) {
      throw new CourseHandler(ErrorStatus.COURSE_PLACE_NOT_FOUND);
    }
    ensureGenerationRequestActive();
    Course course =
        courseRepository.save(
            Course.builder()
                .ownerUserId(userId)
                .title(DEFAULT_TITLE)
                .status(CourseStatus.DRAFT)
                .travelSchedule(travelSchedule)
                .startDate(startDate)
                .endDate(endDate)
                .startTime(DEFAULT_START_TIME)
                .aiRevisionCount(0)
                .build());
    List<CoursePlace> coursePlaces = new ArrayList<>();
    for (int day = 0; day < orderedDays.size(); day++) {
      for (int order = 0; order < orderedDays.get(day).size(); order++) {
        coursePlaces.add(
            CoursePlace.builder()
                .courseId(course.getId())
                .placeId(orderedDays.get(day).get(order))
                .dayNumber(day + 1)
                .visitOrder(order + 1)
                .estimatedStayMinutes(DEFAULT_STAY_MINUTES)
                .travelModeFromPrevious(order == 0 ? null : TRAVEL_MODE_WALK)
                .build());
      }
    }
    List<CoursePlace> saved = coursePlaceRepository.saveAll(coursePlaces);
    return CourseConverter.toCourseDetailResponse(course, saved, placesById);
  }

  private void ensureGenerationRequestActive() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CourseHandler(ErrorStatus.COURSE_GENERATION_TIMEOUT);
    }
  }

  public CourseDtos.CourseDetailResponse editPlaces(
      Long courseId, Long userId, CourseDtos.UpdatePlacesRequest request) {
    Course course = getOwnedDraftCourse(courseId, userId);
    List<CoursePlace> updated = replaceCoursePlaces(courseId, course, request.places());
    Map<Long, Place> placesById = loadPlacesById(updated);
    return CourseConverter.toCourseDetailResponse(course, updated, placesById);
  }

  private Course getOwnedDraftCourse(Long courseId, Long userId) {
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
    return course;
  }

  private List<CoursePlace> replaceCoursePlaces(
      Long courseId, Course course, List<CourseDtos.PlaceOrderItem> items) {
    List<Long> placeIds = items.stream().map(CourseDtos.PlaceOrderItem::placeId).toList();
    Set<Long> distinctPlaceIds = new LinkedHashSet<>(placeIds);
    if (distinctPlaceIds.size() != placeIds.size()) {
      throw new CourseHandler(ErrorStatus.COURSE_DUPLICATE_PLACE);
    }
    if (distinctPlaceIds.size() < MIN_PLACE_COUNT.get(course.getTravelSchedule())) {
      throw new CourseHandler(ErrorStatus.COURSE_INVALID_PLACE_COUNT);
    }

    List<CoursePlace> existing =
        coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(courseId);
    Map<Long, CoursePlace> existingByPlaceId =
        existing.stream().collect(Collectors.toMap(CoursePlace::getPlaceId, Function.identity()));
    Set<Long> newPlaceIds = new LinkedHashSet<>(distinctPlaceIds);
    newPlaceIds.removeAll(existingByPlaceId.keySet());
    if (!newPlaceIds.isEmpty()
        && placeRepository.findAllById(newPlaceIds).size() != newPlaceIds.size()) {
      throw new CourseHandler(ErrorStatus.COURSE_PLACE_NOT_FOUND);
    }

    int totalDays =
        dayCount(course.getTravelSchedule(), course.getStartDate(), course.getEndDate());
    Set<String> daySlotsSeen = new HashSet<>();
    for (CourseDtos.PlaceOrderItem item : items) {
      if (item.dayNumber() < 1 || item.dayNumber() > totalDays) {
        throw new CourseHandler(ErrorStatus.COURSE_DAY_OUT_OF_RANGE);
      }
      if (!daySlotsSeen.add(item.dayNumber() + ":" + item.visitOrder())) {
        throw new CourseHandler(ErrorStatus.COURSE_DUPLICATE_PLACE);
      }
    }

    Map<Integer, List<CourseDtos.PlaceOrderItem>> byDay =
        items.stream().collect(Collectors.groupingBy(CourseDtos.PlaceOrderItem::dayNumber));
    List<CoursePlace> newRows = new ArrayList<>();
    for (Map.Entry<Integer, List<CourseDtos.PlaceOrderItem>> entry : byDay.entrySet()) {
      List<CourseDtos.PlaceOrderItem> dayItems =
          entry.getValue().stream()
              .sorted(Comparator.comparingInt(CourseDtos.PlaceOrderItem::visitOrder))
              .toList();
      for (int i = 0; i < dayItems.size(); i++) {
        CourseDtos.PlaceOrderItem item = dayItems.get(i);
        CoursePlace prior = existingByPlaceId.get(item.placeId());
        int estimatedStayMinutes =
            prior != null ? prior.getEstimatedStayMinutes() : DEFAULT_STAY_MINUTES;
        newRows.add(
            CoursePlace.builder()
                .courseId(courseId)
                .placeId(item.placeId())
                .dayNumber(item.dayNumber())
                .visitOrder(item.visitOrder())
                .estimatedStayMinutes(estimatedStayMinutes)
                .travelModeFromPrevious(i == 0 ? null : TRAVEL_MODE_WALK)
                .build());
      }
    }

    coursePlaceRepository.deleteByCourseId(courseId);
    // CoursePlace는 IDENTITY 전략이라 saveAll이 즉시 INSERT를 실행한다. 위 삭제를 먼저
    // DB에 반영해두지 않으면 재배치된 (day, order) 슬롯이 아직 지워지지 않은 기존 행과
    // 유니크 제약(uk_course_places_course_day_order)에서 충돌한다.
    coursePlaceRepository.flush();
    return coursePlaceRepository.saveAll(newRows);
  }

  private Map<Long, Place> loadPlacesById(List<CoursePlace> coursePlaces) {
    List<Long> placeIds = coursePlaces.stream().map(CoursePlace::getPlaceId).toList();
    return placeRepository.findAllById(placeIds).stream()
        .collect(Collectors.toMap(Place::getId, Function.identity()));
  }

  @Transactional(readOnly = true)
  public CourseDtos.CourseDetailResponse getDraftDetail(Long courseId, Long userId) {
    Course course = getOwnedDraftCourse(courseId, userId);
    List<CoursePlace> coursePlaces =
        coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(courseId);
    Map<Long, Place> placesById = loadPlacesById(coursePlaces);
    return CourseConverter.toCourseDetailResponse(course, coursePlaces, placesById);
  }

  public CourseDtos.ChatResponse requestChatRevision(
      Long courseId, Long userId, CourseDtos.ChatRequest request) {
    Course course = getOwnedDraftCourse(courseId, userId);
    String message = request.message();
    if (message == null || message.length() > CHAT_MESSAGE_MAX_LENGTH) {
      throw new CourseHandler(ErrorStatus.COURSE_CHAT_MESSAGE_TOO_LONG);
    }
    if (course.getAiRevisionCount() >= MAX_AI_REVISIONS) {
      throw new CourseHandler(ErrorStatus.COURSE_AI_REVISION_LIMIT_EXCEEDED);
    }

    List<CoursePlace> currentPlaces =
        coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(courseId);
    Map<Long, Place> placesById = loadPlacesById(currentPlaces);
    Set<Long> currentPlaceIds =
        currentPlaces.stream().map(CoursePlace::getPlaceId).collect(Collectors.toSet());
    List<Place> candidatePlaces =
        placeRepository.findByActiveTrue().stream()
            .filter(place -> !currentPlaceIds.contains(place.getId()))
            .toList();

    GroqCourseChatClient.ChatSuggestion suggestion =
        groqCourseChatClient
            .requestRevision(course, currentPlaces, placesById, candidatePlaces, message)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_AI_REQUEST_FAILED));

    course.incrementAiRevisionCount();

    List<CoursePlace> previewPlaces =
        GroqCourseChatClient.TYPE_COURSE_REVISION.equals(suggestion.type())
            ? buildPreviewCoursePlaces(courseId, suggestion.days(), currentPlaces)
            : List.of();
    Map<Long, Place> previewPlacesById =
        previewPlaces.isEmpty() ? placesById : loadPlacesById(previewPlaces);
    Map<Long, Place> candidatePlacesById =
        candidatePlaces.stream().collect(Collectors.toMap(Place::getId, Function.identity()));
    List<CourseDtos.PlaceRecommendation> recommendations =
        suggestion.recommendations().stream()
            .map(
                recommendation -> {
                  Place place = candidatePlacesById.get(recommendation.placeId());
                  return new CourseDtos.PlaceRecommendation(
                      place.getId(),
                      place.getName(),
                      place.getCategory(),
                      place.getTravelMbtiType(),
                      place.getAddress(),
                      place.getLatitude(),
                      place.getLongitude(),
                      recommendation.reason());
                })
            .toList();
    int remainingRevisions = MAX_AI_REVISIONS - course.getAiRevisionCount();

    return CourseConverter.toChatResponse(
        suggestion.type(),
        suggestion.message(),
        previewPlaces,
        recommendations,
        previewPlacesById,
        remainingRevisions);
  }

  private List<CoursePlace> buildPreviewCoursePlaces(
      Long courseId, List<List<Long>> days, List<CoursePlace> existing) {
    Map<Long, CoursePlace> existingByPlaceId =
        existing.stream().collect(Collectors.toMap(CoursePlace::getPlaceId, Function.identity()));
    List<CoursePlace> preview = new ArrayList<>();
    for (int day = 0; day < days.size(); day++) {
      List<Long> dayPlaceIds = days.get(day);
      for (int order = 0; order < dayPlaceIds.size(); order++) {
        Long placeId = dayPlaceIds.get(order);
        CoursePlace prior = existingByPlaceId.get(placeId);
        preview.add(
            CoursePlace.builder()
                .courseId(courseId)
                .placeId(placeId)
                .dayNumber(day + 1)
                .visitOrder(order + 1)
                .estimatedStayMinutes(prior.getEstimatedStayMinutes())
                .travelModeFromPrevious(order == 0 ? null : TRAVEL_MODE_WALK)
                .build());
      }
    }
    return preview;
  }

  public CourseDtos.CourseDetailResponse applyChatRevision(
      Long courseId, Long userId, CourseDtos.ApplyChatRevisionRequest request) {
    Course course = getOwnedDraftCourse(courseId, userId);
    List<CoursePlace> updated = replaceCoursePlaces(courseId, course, request.places());
    Map<Long, Place> placesById = loadPlacesById(updated);
    return CourseConverter.toCourseDetailResponse(course, updated, placesById);
  }

  public CourseDtos.CourseDetailResponse addRecommendedPlace(
      Long courseId, Long userId, Long placeId) {
    Course course = getOwnedDraftCourse(courseId, userId);

    List<CoursePlace> currentPlaces =
        coursePlaceRepository.findByCourseIdOrderByDayNumberAscVisitOrderAsc(courseId);
    if (currentPlaces.stream().anyMatch(coursePlace -> coursePlace.getPlaceId().equals(placeId))) {
      throw new CourseHandler(ErrorStatus.COURSE_DUPLICATE_PLACE);
    }

    Place newPlace =
        placeRepository
            .findById(placeId)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_PLACE_NOT_FOUND));

    Map<Long, Place> placesById = loadPlacesById(currentPlaces);
    List<List<Long>> days =
        groqCourseChatClient
            .requestPlacementRevision(course, currentPlaces, placesById, newPlace)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_AI_REQUEST_FAILED));

    List<CourseDtos.PlaceOrderItem> items = new ArrayList<>();
    for (int day = 0; day < days.size(); day++) {
      List<Long> dayPlaceIds = days.get(day);
      for (int order = 0; order < dayPlaceIds.size(); order++) {
        items.add(new CourseDtos.PlaceOrderItem(dayPlaceIds.get(order), day + 1, order + 1));
      }
    }

    List<CoursePlace> updated = replaceCoursePlaces(courseId, course, items);
    Map<Long, Place> updatedPlacesById = loadPlacesById(updated);
    return CourseConverter.toCourseDetailResponse(course, updated, updatedPlacesById);
  }

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

    User owner =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    if (explorationParticipantRepository.existsActiveParticipation(userId)) {
      throw new ExplorationHandler(ErrorStatus.DUPLICATE_ACTIVE_PARTICIPATION);
    }

    LocalDateTime now = LocalDateTime.now();
    course.confirm(now, now.plusDays(SHARE_LINK_VALID_DAYS));

    Exploration exploration =
        explorationRepository.save(
            Exploration.builder()
                .courseId(course.getId())
                .status(ExplorationStatus.BEFORE)
                .build());

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
  public CourseDtos.CourseDetailResponse getCourseDetail(Long courseId, Long userId) {
    Course course =
        courseRepository
            .findById(courseId)
            .orElseThrow(() -> new CourseHandler(ErrorStatus.COURSE_NOT_FOUND));
    if (course.getStatus() != CourseStatus.CONFIRMED) {
      throw new CourseHandler(ErrorStatus.COURSE_NOT_FOUND);
    }
    if (course.getShareExpiresAt() == null
        || LocalDateTime.now().isAfter(course.getShareExpiresAt())) {
      boolean participated =
          userId != null
              && explorationRepository
                  .findByCourseId(courseId)
                  .flatMap(
                      exploration ->
                          explorationParticipantRepository.findByExplorationIdAndUserId(
                              exploration.getId(), userId))
                  .isPresent();
      if (!participated) {
        throw new ExplorationHandler(ErrorStatus.SHARE_LINK_EXPIRED);
      }
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
