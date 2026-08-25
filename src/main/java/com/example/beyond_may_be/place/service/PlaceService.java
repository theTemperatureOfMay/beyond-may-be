package com.example.beyond_may_be.place.service;

import com.example.beyond_may_be.place.converter.PlaceConverter;
import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceRecommendationResponse;
import com.example.beyond_may_be.place.repository.PlaceRepository;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

  private static final int RECOMMENDATION_COUNT = 6;

  private final PlaceRepository placeRepository;

  public PlaceRecommendationResponse getRecommendations(TravelPreferenceType type) {
    List<Place> places = new ArrayList<>(placeRepository.findByTravelMbtiTypeAndActiveTrue(type));
    Collections.shuffle(places);
    List<Place> selected = places.stream().limit(RECOMMENDATION_COUNT).toList();
    return PlaceConverter.toRecommendationResponse(selected);
  }
}
