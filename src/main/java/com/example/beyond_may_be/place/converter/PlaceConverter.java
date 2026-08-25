package com.example.beyond_may_be.place.converter;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceRecommendationResponse;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceResponse;
import java.util.List;

public final class PlaceConverter {
  private PlaceConverter() {}

  public static PlaceRecommendationResponse toRecommendationResponse(List<Place> places) {
    return new PlaceRecommendationResponse(
        places.stream().map(PlaceConverter::toPlaceResponse).toList());
  }

  private static PlaceResponse toPlaceResponse(Place place) {
    return new PlaceResponse(
        place.getId(),
        place.getName(),
        place.getCategory(),
        place.getTravelMbtiType(),
        place.getTags(),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        place.getBusinessHours(),
        place.getDescription(),
        place.getThumbnailUrl());
  }
}
