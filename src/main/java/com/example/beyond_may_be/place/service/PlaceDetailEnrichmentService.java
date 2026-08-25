package com.example.beyond_may_be.place.service;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceDetailEnrichmentService {

  private static final Logger log = LoggerFactory.getLogger(PlaceDetailEnrichmentService.class);
  private static final String DESCRIPTION_NOT_AVAILABLE = "상세 설명 정보 없음";
  private static final String BUSINESS_HOURS_NOT_AVAILABLE = "운영시간 정보 없음";

  private final PlaceRepository placeRepository;
  private final TourApiPlaceDetailClient detailClient;

  @Async
  public void enrichAsync(List<Long> placeIds) {
    List<Place> places;
    try {
      places = placeRepository.findAllById(placeIds);
    } catch (RuntimeException ignored) {
      log.warn("추천 장소 상세정보 보강 대상을 불러오지 못했습니다.");
      return;
    }

    for (Place place : places) {
      try {
        enrich(place);
      } catch (RuntimeException ignored) {
        log.warn("추천 장소 상세정보 보강에 실패했습니다. placeId={}", place.getId());
      }
    }
  }

  public boolean enrich(Place place) {
    boolean descriptionUpdated = false;
    boolean businessHoursUpdated = false;
    TourApiPlaceDetailClient.TourApiPlaceDetailException firstFailure = null;
    try {
      descriptionUpdated = enrichDescription(place);
    } catch (TourApiPlaceDetailClient.TourApiPlaceDetailException exception) {
      firstFailure = exception;
    }
    try {
      businessHoursUpdated = enrichBusinessHours(place);
    } catch (TourApiPlaceDetailClient.TourApiPlaceDetailException exception) {
      if (firstFailure == null) {
        firstFailure = exception;
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
    return descriptionUpdated || businessHoursUpdated;
  }

  private boolean enrichDescription(Place place) {
    if (!isBlank(place.getDescription())) {
      return false;
    }
    String description =
        place.getTourContentId() == null
            ? null
            : detailClient.fetchDescription(place.getTourContentId());
    if (isBlank(description)) {
      description = DESCRIPTION_NOT_AVAILABLE;
    }
    placeRepository.updateDescriptionIfMissing(place.getId(), description);
    return true;
  }

  private boolean enrichBusinessHours(Place place) {
    if (!isBlank(place.getBusinessHours())) {
      return false;
    }
    String businessHours =
        place.getTourContentId() == null || place.getTourContentTypeId() == null
            ? null
            : detailClient.fetchBusinessHours(
                place.getTourContentId(), place.getTourContentTypeId());
    if (isBlank(businessHours)) {
      businessHours = BUSINESS_HOURS_NOT_AVAILABLE;
    }
    placeRepository.updateBusinessHoursIfMissing(place.getId(), businessHours);
    return true;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
