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

  private final PlaceRepository placeRepository;
  private final TourApiPlaceDetailClient detailClient;

  @Async
  public void enrichAsync(List<Long> placeIds) {
    // 7-1. 추천 응답에 실제 포함된 장소만 별도 스레드에서 불러와 HTTP 응답을 기다리게 하지 않는다.
    List<Place> places;
    try {
      places = placeRepository.findAllById(placeIds);
    } catch (RuntimeException ignored) {
      log.warn("추천 장소 상세정보 보강 대상을 불러오지 못했습니다.");
      return;
    }
    // 7-2. 설명과 운영시간을 서로 독립적으로 처리해 한 필드의 실패가 다른 보강을 막지 않는다.
    for (Place place : places) {
      enrichDescription(place);
      enrichBusinessHours(place);
    }
  }

  private void enrichDescription(Place place) {
    if (place.getTourContentId() == null || !isBlank(place.getDescription())) {
      return;
    }
    try {
      String description = detailClient.fetchDescription(place.getTourContentId());
      if (!isBlank(description)) {
        placeRepository.updateDescriptionIfMissing(place.getId(), description);
      }
    } catch (RuntimeException ignored) {
      log.warn("추천 장소 설명 보강에 실패했습니다. placeId={}", place.getId());
    }
  }

  private void enrichBusinessHours(Place place) {
    if (place.getTourContentId() == null
        || place.getTourContentTypeId() == null
        || !isBlank(place.getBusinessHours())) {
      return;
    }
    try {
      String businessHours =
          detailClient.fetchBusinessHours(place.getTourContentId(), place.getTourContentTypeId());
      if (!isBlank(businessHours)) {
        placeRepository.updateBusinessHoursIfMissing(place.getId(), businessHours);
      }
    } catch (RuntimeException ignored) {
      log.warn("추천 장소 운영시간 보강에 실패했습니다. placeId={}", place.getId());
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
