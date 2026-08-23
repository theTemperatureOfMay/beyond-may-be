package com.example.beyond_may_be.place.service;

import com.example.beyond_may_be.apiPayload.code.status.ErrorStatus;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;
import com.example.beyond_may_be.place.converter.PlaceConverter;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceService {

  private final PlaceRepository placeRepository;
  private final PlaceDetailEnrichmentService enrichmentService;

  public PlaceDtos.DetailResponse getDetail(Long placeId) {
    Place place = findActivePlace(placeId);
    try {
      if (enrichmentService.enrich(place)) {
        place = findActivePlace(placeId);
      }
    } catch (TourApiPlaceDetailClient.TourApiPlaceDetailException ignored) {
      throw new GeneralException(ErrorStatus.PLACE_DETAIL_UNAVAILABLE);
    }
    return PlaceConverter.toDetailResponse(place);
  }

  private Place findActivePlace(Long placeId) {
    return placeRepository
        .findByIdAndActiveTrue(placeId)
        .orElseThrow(() -> new GeneralException(ErrorStatus.PLACE_DETAIL_NOT_FOUND));
  }
}
