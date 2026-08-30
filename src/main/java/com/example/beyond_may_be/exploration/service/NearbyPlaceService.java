package com.example.beyond_may_be.exploration.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.handler.ExplorationHandler;
import com.example.beyond_may_be.common.util.GeoDistanceCalculator;
import com.example.beyond_may_be.course.domain.CoursePlace;
import com.example.beyond_may_be.course.repository.CoursePlaceRepository;
import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.repository.ExplorationRepository;
import com.example.beyond_may_be.place.converter.PlaceConverter;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.place.service.TourApiClassificationCatalog;
import com.example.beyond_may_be.place.service.TourApiNearbyPlaceClient;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NearbyPlaceService {

  private static final double RADIUS_METERS = 1_000;
  private static final int RESULT_LIMIT = 3;
  private static final int MAX_VARCHAR_LENGTH = 255;

  private final ExplorationRepository explorationRepository;
  private final ExplorationParticipantRepository participantRepository;
  private final CoursePlaceRepository coursePlaceRepository;
  private final PlaceRepository placeRepository;
  private final TourApiNearbyPlaceClient tourApiNearbyPlaceClient;

  public PlaceDtos.NearbyPlacesResponse getNearbyPlaces(
      Long explorationId, Long userId, BigDecimal latitude, BigDecimal longitude) {
    Exploration exploration =
        explorationRepository
            .findById(explorationId)
            .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_FOUND));
    if (exploration.getStatus() != ExplorationStatus.ONGOING) {
      throw new ExplorationHandler(ErrorStatus.EXPLORATION_NOT_ONGOING);
    }
    participantRepository
        .findByExplorationIdAndUserId(explorationId, userId)
        .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
        .orElseThrow(() -> new ExplorationHandler(ErrorStatus.EXPLORATION_PARTICIPANT_FORBIDDEN));

    Set<Long> coursePlaceIds =
        coursePlaceRepository
            .findByCourseIdOrderByDayNumberAscVisitOrderAsc(exploration.getCourseId())
            .stream()
            .map(CoursePlace::getPlaceId)
            .collect(Collectors.toSet());
    List<NearbyCandidate> candidates =
        tourApiNearbyPlaceClient.fetchNearbyPlaces(latitude, longitude).stream()
            .map(this::toCandidate)
            .flatMap(Optional::stream)
            .toList();
    List<Long> candidateContentIds = candidates.stream().map(NearbyCandidate::contentId).toList();
    Set<Long> candidateContentIdSet = Set.copyOf(candidateContentIds);
    if (!candidates.isEmpty()) {
      Set<Long> existingContentIds =
          placeRepository.findExistingTourContentIds(candidateContentIds);
      candidates.stream()
          .filter(candidate -> !existingContentIds.contains(candidate.contentId()))
          .forEach(this::save);
    }

    // ponytail: MVP 장소 수에서는 활성 장소를 한 번 읽어 거리 계산한다. 커지면 DB 공간 쿼리로 교체한다.
    List<Place> activePlaces = placeRepository.findAllByActiveTrue();
    List<PlaceDistance> places =
        findNearbyPlaces(activePlaces, candidateContentIdSet, coursePlaceIds, latitude, longitude);
    if (places.isEmpty() && !candidateContentIdSet.isEmpty()) {
      places = findNearbyPlaces(activePlaces, Set.of(), coursePlaceIds, latitude, longitude);
    }
    return new PlaceDtos.NearbyPlacesResponse(
        places.stream()
            .map(
                place ->
                    PlaceConverter.toNearbyPlaceResponse(
                        place.place(), Math.round(place.distanceMeters())))
            .toList());
  }

  private Optional<NearbyCandidate> toCandidate(TourApiNearbyPlaceClient.NearbyItem item) {
    if (item == null
        || !"5".equals(item.areaCode())
        || isBlank(item.title())
        || isBlank(item.address())
        || isBlank(item.contentId())
        || isBlank(item.contentTypeId())
        || isBlank(item.latitude())
        || isBlank(item.longitude())
        || isBlank(item.largeCategoryCode())) {
      return Optional.empty();
    }
    TravelPreferenceType type =
        switch (item.largeCategoryCode()) {
          case "NA" -> TravelPreferenceType.THINKER;
          case "FD" -> TravelPreferenceType.FOODIE;
          case "VE" -> TravelPreferenceType.ARTIST;
          case "HS" -> TravelPreferenceType.REMEMBERER;
          default -> null;
        };
    if (type == null) {
      return Optional.empty();
    }
    try {
      long contentId = Long.parseLong(item.contentId());
      int contentTypeId = Integer.parseInt(item.contentTypeId());
      BigDecimal candidateLatitude = new BigDecimal(item.latitude());
      BigDecimal candidateLongitude = new BigDecimal(item.longitude());
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
          || candidateLatitude.compareTo(BigDecimal.valueOf(-90)) < 0
          || candidateLatitude.compareTo(BigDecimal.valueOf(90)) > 0
          || candidateLongitude.compareTo(BigDecimal.valueOf(-180)) < 0
          || candidateLongitude.compareTo(BigDecimal.valueOf(180)) > 0) {
        return Optional.empty();
      }
      TourApiClassificationCatalog.Classification classification =
          TourApiClassificationCatalog.resolve(
              item.largeCategoryCode(), item.middleCategoryCode(), item.smallCategoryCode());
      return Optional.of(
          new NearbyCandidate(
              contentId,
              contentTypeId,
              name,
              classification.category(),
              type,
              classification.tags(),
              address,
              candidateLatitude,
              candidateLongitude,
              thumbnailUrl));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private void save(NearbyCandidate candidate) {
    placeRepository.insertTourApiPlace(
        candidate.contentId(),
        candidate.contentTypeId(),
        candidate.name(),
        candidate.category(),
        candidate.type().name(),
        candidate.tags().stream().collect(Collectors.joining("\",\"", "[\"", "\"]")),
        candidate.address(),
        candidate.latitude(),
        candidate.longitude(),
        candidate.thumbnailUrl());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private boolean fitsColumn(String value) {
    return value.length() <= MAX_VARCHAR_LENGTH;
  }

  private PlaceDistance distance(Place place, BigDecimal latitude, BigDecimal longitude) {
    return new PlaceDistance(
        place,
        GeoDistanceCalculator.distanceMeters(
            latitude.doubleValue(),
            longitude.doubleValue(),
            place.getLatitude().doubleValue(),
            place.getLongitude().doubleValue()));
  }

  private List<PlaceDistance> findNearbyPlaces(
      List<Place> activePlaces,
      Set<Long> contentIds,
      Set<Long> coursePlaceIds,
      BigDecimal latitude,
      BigDecimal longitude) {
    return activePlaces.stream()
        .filter(
            place ->
                contentIds.isEmpty()
                    || (place.getTourContentId() != null
                        && contentIds.contains(place.getTourContentId())))
        .filter(place -> !coursePlaceIds.contains(place.getId()))
        .map(place -> distance(place, latitude, longitude))
        .filter(place -> place.distanceMeters() <= RADIUS_METERS)
        .sorted(Comparator.comparingDouble(PlaceDistance::distanceMeters))
        .limit(RESULT_LIMIT)
        .toList();
  }

  private record PlaceDistance(Place place, double distanceMeters) {}

  private record NearbyCandidate(
      long contentId,
      int contentTypeId,
      String name,
      String category,
      TravelPreferenceType type,
      List<String> tags,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String thumbnailUrl) {}
}
