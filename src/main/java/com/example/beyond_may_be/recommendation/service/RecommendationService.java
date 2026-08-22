package com.example.beyond_may_be.recommendation.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.RecommendationHandler;
import com.example.beyond_may_be.apiPayload.exception.handler.UserHandler;
import com.example.beyond_may_be.course.domain.enums.TravelSchedule;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.place.service.PlaceDetailEnrichmentService;
import com.example.beyond_may_be.place.service.TourApiSyncClient;
import com.example.beyond_may_be.place.service.TourApiSyncClient.SyncItem;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import com.example.beyond_may_be.recommendation.converter.RecommendationConverter;
import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import com.example.beyond_may_be.recommendation.dto.RecommendationDtos;
import com.example.beyond_may_be.recommendation.repository.RecommendationSetRepository;
import com.example.beyond_may_be.user.domain.User;
import com.example.beyond_may_be.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final int RECOMMENDATION_COUNT = 20;
  private static final int MAX_VARCHAR_LENGTH = 255;
  private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
  private static final List<TravelPreferenceType> TYPE_PRIORITY =
      List.of(
          TravelPreferenceType.THINKER,
          TravelPreferenceType.FOODIE,
          TravelPreferenceType.ARTIST,
          TravelPreferenceType.REMEMBERER);

  private final UserRepository userRepository;
  private final PlaceRepository placeRepository;
  private final RecommendationSetRepository recommendationSetRepository;
  private final TourApiSyncClient tourApiSyncClient;
  private final PlaceDetailEnrichmentService placeDetailEnrichmentService;
  private final GroqRecommendationClient groqRecommendationClient;
  private final TransactionTemplate transactionTemplate;

  public RecommendationDtos.RecommendationResponse createOrGetCurrent(
      Long userId, RecommendationDtos.CreateRequest request) {
    // 1. 여행 일정이 유효하고 사용자의 최종 여행 성향이 저장되어 있는지 확인한다.
    validatePeriod(request);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    if (user.getPreferenceType() == null) {
      throw new RecommendationHandler(ErrorStatus.RECOMMENDATION_PREFERENCE_REQUIRED);
    }

    // 2. 동일 일정으로 만든 현재 추천 세트가 있으면 새로 만들지 않고 그대로 반환한다.
    Optional<RecommendationSet> current = recommendationSetRepository.findByUserId(userId);
    if (current.isPresent() && samePeriod(current.get(), request)) {
      return responseFor(current.get());
    }

    // 3. 성향별 장소가 최초 할당량보다 부족하면 트랜잭션 밖에서 TourAPI 변경분을 한 번 조회한다.
    List<SyncItem> syncItems =
        hasInitialShortage(user, placeRepository.findAllByActiveTrue())
            ? fetchChangedPlaces()
            : List.of();
    // 4. 사용자 잠금 트랜잭션에서 신규 장소를 저장하고 할당량·AI 후보·규칙 폴백을 준비한다.
    SelectionPreparation preparation =
        Objects.requireNonNull(
            transactionTemplate.execute(status -> prepareSelection(userId, request, syncItems)));
    if (preparation.response() != null) {
      return preparation.response();
    }
    SelectionPlan plan = Objects.requireNonNull(preparation.plan());
    // 5. DB 트랜잭션 밖에서 AI가 후보를 선별한다. 실패하면 빈 결과로 6단계 폴백을 유도한다.
    List<Long> aiPlaceIds = plan.target() == 0 ? List.of() : rankSafely(plan.rankRequest());
    // 6. 최신 DB 상태로 AI 결과를 검증하고 현재 추천 세트를 저장하거나 교체한다.
    CreationResult result =
        Objects.requireNonNull(
            transactionTemplate.execute(
                status -> createOrReplaceCurrent(userId, request, plan, aiPlaceIds)));
    // 7. 새 추천의 빈 상세정보 보강만 비동기로 예약하고 저장된 추천 응답을 즉시 반환한다.
    scheduleEnrichment(result);
    return result.response();
  }

  private SelectionPreparation prepareSelection(
      Long userId, RecommendationDtos.CreateRequest request, List<SyncItem> syncItems) {
    // 4-1. 동시 요청이 같은 추천을 중복 생성하지 않도록 사용자를 잠그고 현재 세트를 재확인한다.
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    if (user.getPreferenceType() == null) {
      throw new RecommendationHandler(ErrorStatus.RECOMMENDATION_PREFERENCE_REQUIRED);
    }
    Optional<RecommendationSet> current = recommendationSetRepository.findByUserId(userId);
    if (current.isPresent() && samePeriod(current.get(), request)) {
      return new SelectionPreparation(responseFor(current.get()), null);
    }

    // 4-2. 유효한 TourAPI 신규 장소를 저장했다면 최신 DB 목록으로 추천 계획을 다시 만든다.
    List<Place> activePlaces = placeRepository.findAllByActiveTrue();
    if (hasInitialShortage(user, activePlaces) && syncNewPlaces(syncItems)) {
      activePlaces = placeRepository.findAllByActiveTrue();
    }
    return new SelectionPreparation(null, selectionPlan(userId, user, request, activePlaces));
  }

  private CreationResult createOrReplaceCurrent(
      Long userId,
      RecommendationDtos.CreateRequest request,
      SelectionPlan preparedPlan,
      List<Long> aiPlaceIds) {
    // 6-1. AI 호출 중 발생한 동시 요청을 반영하도록 사용자를 잠그고 현재 세트를 다시 확인한다.
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    if (user.getPreferenceType() == null) {
      throw new RecommendationHandler(ErrorStatus.RECOMMENDATION_PREFERENCE_REQUIRED);
    }
    Optional<RecommendationSet> current = recommendationSetRepository.findByUserId(userId);
    if (current.isPresent() && samePeriod(current.get(), request)) {
      return new CreationResult(responseFor(current.get()), false);
    }

    // 6-2. AI가 전달받은 활성 후보와 유형별 할당량을 정확히 지켰을 때만 AI 결과를 사용한다.
    List<Place> activePlaces = placeRepository.findAllByActiveTrue();
    Map<Long, Place> activePlacesById =
        activePlaces.stream()
            .filter(Place::isActive)
            .collect(Collectors.toMap(Place::getId, Function.identity()));
    boolean validAiResult = validAiSelection(aiPlaceIds, preparedPlan, activePlacesById);
    if (!validAiResult && preparedPlan.target() > 0) {
      log.warn("AI 추천 결과 검증에 실패해 규칙 기반 추천 전체를 사용합니다.");
    }
    List<Place> selected =
        validAiResult
            ? aiPlaceIds.stream().map(activePlacesById::get).toList()
            : selectionPlan(userId, user, request, activePlaces).fallbackPlaces();
    List<Long> selectedIds = selected.stream().map(Place::getId).toList();
    // 6-3. 최초 요청은 새 세트를 저장하고, 기간 변경은 같은 ID의 세트와 반응 기록을 교체한다.
    RecommendationSet recommendationSet =
        current.orElseGet(
            () ->
                RecommendationSet.builder()
                    .userId(userId)
                    .travelSchedule(request.travelSchedule())
                    .startDate(request.startDate())
                    .endDate(request.endDate())
                    .recommendedPlaceIds(selectedIds)
                    .likedPlaceIds(List.of())
                    .dislikedPlaceIds(List.of())
                    .build());
    if (current.isPresent()) {
      recommendationSet.replace(
          request.travelSchedule(), request.startDate(), request.endDate(), selectedIds);
    }
    RecommendationSet saved = recommendationSetRepository.save(recommendationSet);
    return new CreationResult(
        RecommendationConverter.toRecommendationResponse(
            saved, minimumSelectionCount(request.travelSchedule()), 1, selected),
        true);
  }

  private void scheduleEnrichment(CreationResult result) {
    List<Long> placeIds =
        result.response().batch().places().stream()
            .map(RecommendationDtos.PlaceResponse::placeId)
            .toList();
    if (!result.created() || placeIds.isEmpty()) {
      return;
    }
    try {
      placeDetailEnrichmentService.enrichAsync(placeIds);
    } catch (RuntimeException ignored) {
      log.warn("추천 장소 상세정보 보강 작업을 예약하지 못했습니다.");
    }
  }

  private List<Long> rankSafely(GroqRecommendationClient.RankRequest request) {
    try {
      return groqRecommendationClient.rank(request);
    } catch (RuntimeException exception) {
      log.warn(
          "Groq 추천 처리 중 예기치 않은 오류로 규칙 기반 추천을 사용합니다. cause={}",
          exception.getClass().getSimpleName());
      return List.of();
    }
  }

  private List<SyncItem> fetchChangedPlaces() {
    try {
      return tourApiSyncClient.fetchChangedPlaces();
    } catch (RuntimeException exception) {
      log.warn(
          "TourAPI 변경분 처리 중 예기치 않은 오류로 현재 DB 장소만 사용합니다. cause={}",
          exception.getClass().getSimpleName());
      return List.of();
    }
  }

  private boolean hasInitialShortage(User user, List<Place> activePlaces) {
    Map<TravelPreferenceType, Integer> counts = emptyCounts();
    activePlaces.stream()
        .filter(Place::isActive)
        .forEach(
            place ->
                counts.computeIfPresent(place.getTravelMbtiType(), (type, count) -> count + 1));
    Map<TravelPreferenceType, Integer> quotas = initialQuotas(user);
    return TYPE_PRIORITY.stream().anyMatch(type -> counts.get(type) < quotas.get(type));
  }

  // TourAPI 변경분에서 지원 분류와 필수 값이 유효하며 DB에 없는 장소만 저장한다.
  private boolean syncNewPlaces(List<SyncItem> syncItems) {
    List<SyncPlace> candidates =
        syncItems.stream()
            .map(this::toSyncPlace)
            .flatMap(Optional::stream)
            .collect(
                Collectors.toMap(
                    SyncPlace::contentId, Function.identity(), (first, ignored) -> first))
            .values()
            .stream()
            .toList();
    if (candidates.isEmpty()) {
      return false;
    }

    Set<Long> existingIds =
        placeRepository.findExistingTourContentIds(
            candidates.stream().map(SyncPlace::contentId).toList());
    boolean attempted = false;
    for (SyncPlace place : candidates) {
      if (existingIds.contains(place.contentId())) {
        continue;
      }
      attempted = true;
      placeRepository.insertTourApiPlace(
          place.contentId(),
          place.contentTypeId(),
          place.name(),
          place.category(),
          place.travelMbtiType().name(),
          "[\"" + place.category() + "\"]",
          place.address(),
          place.latitude(),
          place.longitude(),
          place.thumbnailUrl());
    }
    return attempted;
  }

  private Optional<SyncPlace> toSyncPlace(SyncItem item) {
    if (item == null
        || !"1".equals(item.showFlag())
        || isBlank(item.title())
        || isBlank(item.address())
        || isBlank(item.contentId())
        || isBlank(item.contentTypeId())
        || isBlank(item.latitude())
        || isBlank(item.longitude())
        || isBlank(item.largeCategoryCode())) {
      return Optional.empty();
    }
    TravelPreferenceType type;
    String category;
    // TourAPI 신분류 대분류 코드를 프로젝트의 네 여행 성향으로 매핑한다.
    switch (item.largeCategoryCode()) {
      case "NA" -> {
        type = TravelPreferenceType.THINKER;
        category = "자연 관광";
      }
      case "FD" -> {
        type = TravelPreferenceType.FOODIE;
        category = "음식";
      }
      case "VE" -> {
        type = TravelPreferenceType.ARTIST;
        category = "문화 관광";
      }
      case "HS" -> {
        type = TravelPreferenceType.REMEMBERER;
        category = "역사 관광";
      }
      default -> {
        return Optional.empty();
      }
    }
    try {
      long contentId = Long.parseLong(item.contentId());
      int contentTypeId = Integer.parseInt(item.contentTypeId());
      BigDecimal latitude = new BigDecimal(item.latitude());
      BigDecimal longitude = new BigDecimal(item.longitude());
      String name = item.title().trim();
      String address =
          isBlank(item.addressDetail())
              ? item.address().trim()
              : item.address().trim() + " " + item.addressDetail().trim();
      String thumbnailUrl = isBlank(item.thumbnailUrl()) ? null : item.thumbnailUrl().trim();
      if (contentId <= 0
          || contentTypeId <= 0
          || contentTypeId == 32
          || !fitsColumn(name)
          || !fitsColumn(address)
          || (thumbnailUrl != null && !fitsColumn(thumbnailUrl))
          || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
          || latitude.compareTo(BigDecimal.valueOf(90)) > 0
          || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
          || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
        return Optional.empty();
      }
      return Optional.of(
          new SyncPlace(
              contentId,
              contentTypeId,
              name,
              category,
              type,
              address,
              latitude,
              longitude,
              thumbnailUrl));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private boolean fitsColumn(String value) {
    return value.length() <= MAX_VARCHAR_LENGTH;
  }

  private record SyncPlace(
      long contentId,
      int contentTypeId,
      String name,
      String category,
      TravelPreferenceType travelMbtiType,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String thumbnailUrl) {}

  private record SelectionPreparation(
      RecommendationDtos.RecommendationResponse response, SelectionPlan plan) {}

  private record CreationResult(
      RecommendationDtos.RecommendationResponse response, boolean created) {}

  private record SelectionPlan(
      GroqRecommendationClient.RankRequest rankRequest,
      List<Place> fallbackPlaces,
      Set<Long> candidateIds,
      Map<TravelPreferenceType, Integer> quotas,
      int target) {}

  private RecommendationDtos.RecommendationResponse responseFor(
      RecommendationSet recommendationSet) {
    List<Long> ids = recommendationSet.getRecommendedPlaceIds();
    int batchNumber = Math.max(1, (ids.size() + RECOMMENDATION_COUNT - 1) / RECOMMENDATION_COUNT);
    int fromIndex = Math.min((batchNumber - 1) * RECOMMENDATION_COUNT, ids.size());
    List<Long> batchIds = ids.subList(fromIndex, ids.size());
    Map<Long, Place> activePlacesById =
        placeRepository.findAllById(batchIds).stream()
            .filter(Place::isActive)
            .collect(Collectors.toMap(Place::getId, Function.identity()));
    List<Place> places =
        batchIds.stream().map(activePlacesById::get).filter(Objects::nonNull).toList();
    return RecommendationConverter.toRecommendationResponse(
        recommendationSet,
        minimumSelectionCount(recommendationSet.getTravelSchedule()),
        batchNumber,
        places);
  }

  private SelectionPlan selectionPlan(
      Long userId, User user, RecommendationDtos.CreateRequest request, List<Place> activePlaces) {
    // 4-3. 활성 장소를 성향별로 묶고, 부족한 유형의 몫은 실제 후보가 있는 유형으로 재배분한다.
    Map<TravelPreferenceType, List<Place>> placesByType = new EnumMap<>(TravelPreferenceType.class);
    for (TravelPreferenceType type : TYPE_PRIORITY) {
      placesByType.put(type, new ArrayList<>());
    }
    for (Place place : activePlaces) {
      if (place.isActive() && placesByType.containsKey(place.getTravelMbtiType())) {
        placesByType.get(place.getTravelMbtiType()).add(place);
      }
    }
    Map<TravelPreferenceType, Integer> availability = emptyCounts();
    TYPE_PRIORITY.forEach(type -> availability.put(type, placesByType.get(type).size()));
    Map<TravelPreferenceType, Integer> quotas =
        redistributeShortage(initialQuotas(user), scores(user), availability);

    Map<TravelPreferenceType, List<Place>> fallbackByType =
        new EnumMap<>(TravelPreferenceType.class);
    List<GroqRecommendationClient.Candidate> aiCandidates = new ArrayList<>();
    // 4-4. 같은 사용자·일정에는 같은 순서를 만들고, 할당량의 3배만 AI 후보로 전달한다.
    for (TravelPreferenceType type : TYPE_PRIORITY) {
      List<Place> candidates = placesByType.get(type);
      candidates.sort(Comparator.comparing(Place::getId));
      Collections.shuffle(
          candidates,
          new Random(Objects.hash(userId, request.startDate(), request.endDate(), type.name())));
      fallbackByType.put(type, candidates.stream().limit(quotas.get(type)).toList());
      candidates.stream()
          .limit((long) quotas.get(type) * 3)
          .map(this::toAiCandidate)
          .forEach(aiCandidates::add);
    }

    List<Place> fallbackPlaces = interleave(fallbackByType, sum(quotas));
    GroqRecommendationClient.RankRequest rankRequest =
        new GroqRecommendationClient.RankRequest(
            Map.copyOf(scores(user)),
            Map.copyOf(quotas),
            request.travelSchedule(),
            request.startDate(),
            request.endDate(),
            List.copyOf(aiCandidates));
    return new SelectionPlan(
        rankRequest,
        fallbackPlaces,
        aiCandidates.stream()
            .map(GroqRecommendationClient.Candidate::placeId)
            .collect(Collectors.toUnmodifiableSet()),
        Map.copyOf(quotas),
        sum(quotas));
  }

  private GroqRecommendationClient.Candidate toAiCandidate(Place place) {
    return new GroqRecommendationClient.Candidate(
        place.getId(),
        place.getTravelMbtiType(),
        place.getName(),
        place.getCategory(),
        place.getTags() == null ? List.of() : List.copyOf(place.getTags()),
        nonBlank(place.getAddress()),
        nonBlank(place.getBusinessHours()));
  }

  private String nonBlank(String value) {
    return isBlank(value) ? null : value;
  }

  private List<Place> interleave(
      Map<TravelPreferenceType, List<Place>> selectedByType, int target) {
    List<Place> selected = new ArrayList<>();
    for (int index = 0; selected.size() < target; index++) {
      boolean added = false;
      for (TravelPreferenceType type : TYPE_PRIORITY) {
        List<Place> places = selectedByType.get(type);
        if (index < places.size()) {
          selected.add(places.get(index));
          added = true;
        }
      }
      if (!added) {
        break;
      }
    }
    return selected;
  }

  private boolean validAiSelection(
      List<Long> aiPlaceIds, SelectionPlan plan, Map<Long, Place> activePlacesById) {
    // AI는 전달받은 활성 장소만 중복 없이 고르고 유형별 할당량도 정확히 맞춰야 한다.
    if (aiPlaceIds == null
        || aiPlaceIds.size() != plan.target()
        || new HashSet<>(aiPlaceIds).size() != aiPlaceIds.size()
        || !plan.candidateIds().containsAll(aiPlaceIds)
        || !activePlacesById.keySet().containsAll(aiPlaceIds)) {
      return false;
    }
    Map<TravelPreferenceType, Integer> counts = emptyCounts();
    for (Long placeId : aiPlaceIds) {
      TravelPreferenceType type = activePlacesById.get(placeId).getTravelMbtiType();
      if (!counts.containsKey(type)) {
        return false;
      }
      counts.put(type, counts.get(type) + 1);
    }
    return counts.equals(plan.quotas());
  }

  private Map<TravelPreferenceType, Integer> redistributeShortage(
      Map<TravelPreferenceType, Integer> initial,
      Map<TravelPreferenceType, Integer> scores,
      Map<TravelPreferenceType, Integer> availability) {
    // 후보가 부족해 비는 몫을 남은 성향 점수 비율대로 재배분하되 총 추천 수는 20개를 넘지 않는다.
    Map<TravelPreferenceType, Integer> quotas = emptyCounts();
    TYPE_PRIORITY.forEach(
        type -> quotas.put(type, Math.min(initial.get(type), availability.get(type))));
    int target =
        Math.min(
            RECOMMENDATION_COUNT, availability.values().stream().mapToInt(Integer::intValue).sum());

    while (sum(quotas) < target) {
      int remaining = target - sum(quotas);
      List<TravelPreferenceType> eligible =
          TYPE_PRIORITY.stream().filter(type -> quotas.get(type) < availability.get(type)).toList();
      long totalScore = eligible.stream().mapToLong(scores::get).sum();
      if (totalScore == 0) {
        for (TravelPreferenceType type : eligible) {
          if (remaining-- == 0) {
            break;
          }
          quotas.put(type, quotas.get(type) + 1);
        }
        continue;
      }

      Map<TravelPreferenceType, Long> remainders = new EnumMap<>(TravelPreferenceType.class);
      for (TravelPreferenceType type : eligible) {
        long scaled = (long) scores.get(type) * remaining;
        int addition =
            Math.min((int) (scaled / totalScore), availability.get(type) - quotas.get(type));
        quotas.put(type, quotas.get(type) + addition);
        remainders.put(type, scaled % totalScore);
      }
      if (sum(quotas) < target) {
        eligible.stream()
            .filter(type -> quotas.get(type) < availability.get(type))
            .sorted(Comparator.comparingLong(remainders::get).reversed())
            .limit(target - sum(quotas))
            .forEach(type -> quotas.put(type, quotas.get(type) + 1));
      }
    }
    return quotas;
  }

  private Map<TravelPreferenceType, Integer> initialQuotas(User user) {
    // 네 성향 점수 비율을 20곳에 적용하고 나눗셈 나머지가 큰 성향부터 남은 자리를 배정한다.
    Map<TravelPreferenceType, Integer> scores = scores(user);
    long totalScore = scores.values().stream().mapToLong(Integer::longValue).sum();
    Map<TravelPreferenceType, Integer> quotas = emptyCounts();
    if (totalScore == 0) {
      quotas.put(user.getPreferenceType(), RECOMMENDATION_COUNT);
      return quotas;
    }

    Map<TravelPreferenceType, Long> remainders = new EnumMap<>(TravelPreferenceType.class);
    int allocated = 0;
    for (TravelPreferenceType type : TYPE_PRIORITY) {
      long scaled = (long) scores.get(type) * RECOMMENDATION_COUNT;
      int quota = (int) (scaled / totalScore);
      quotas.put(type, quota);
      remainders.put(type, scaled % totalScore);
      allocated += quota;
    }
    TYPE_PRIORITY.stream()
        .sorted(
            Comparator.comparingLong((TravelPreferenceType type) -> remainders.get(type))
                .reversed())
        .limit(RECOMMENDATION_COUNT - allocated)
        .forEach(type -> quotas.put(type, quotas.get(type) + 1));
    return quotas;
  }

  private Map<TravelPreferenceType, Integer> scores(User user) {
    Map<TravelPreferenceType, Integer> scores = new EnumMap<>(TravelPreferenceType.class);
    scores.put(TravelPreferenceType.THINKER, orZero(user.getThinkerScore()));
    scores.put(TravelPreferenceType.FOODIE, orZero(user.getFoodieScore()));
    scores.put(TravelPreferenceType.ARTIST, orZero(user.getArtistScore()));
    scores.put(TravelPreferenceType.REMEMBERER, orZero(user.getRemembererScore()));
    return scores;
  }

  private Map<TravelPreferenceType, Integer> emptyCounts() {
    Map<TravelPreferenceType, Integer> counts = new EnumMap<>(TravelPreferenceType.class);
    TYPE_PRIORITY.forEach(type -> counts.put(type, 0));
    return counts;
  }

  private int sum(Map<TravelPreferenceType, Integer> counts) {
    return counts.values().stream().mapToInt(Integer::intValue).sum();
  }

  private void validatePeriod(RecommendationDtos.CreateRequest request) {
    // 정상적인 날짜가 들어있지 않을 경우 예외 처리
    if (request.travelSchedule() == null
        || request.startDate() == null
        || request.endDate() == null
        || request.startDate().isBefore(LocalDate.now(SERVICE_ZONE))) {
      throw new RecommendationHandler(ErrorStatus.RECOMMENDATION_INVALID_PERIOD);
    }
    long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate());
    // ENUM 매핑 (1박 2일, 2박 3일 등)
    boolean valid =
        switch (request.travelSchedule()) {
          case DAY_TRIP -> days == 0;
          case ONE_NIGHT_TWO_DAYS -> days == 1;
          case TWO_NIGHTS_THREE_DAYS -> days == 2;
          case CUSTOM -> days >= 3;
        };
    if (!valid) {
      throw new RecommendationHandler(ErrorStatus.RECOMMENDATION_INVALID_PERIOD);
    }
  }

  private boolean samePeriod(
      RecommendationSet recommendationSet, RecommendationDtos.CreateRequest request) {
    return recommendationSet.getTravelSchedule() == request.travelSchedule()
        && recommendationSet.getStartDate().equals(request.startDate())
        && recommendationSet.getEndDate().equals(request.endDate());
  }

  private int minimumSelectionCount(TravelSchedule travelSchedule) {
    return switch (travelSchedule) {
      case DAY_TRIP -> 3;
      case ONE_NIGHT_TWO_DAYS -> 5;
      case TWO_NIGHTS_THREE_DAYS -> 7;
      case CUSTOM -> 20;
    };
  }

  private int orZero(Integer value) {
    return value == null ? 0 : Math.max(0, value);
  }
}
