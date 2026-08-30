package com.example.beyond_may_be.place.converter;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.place.dto.PlaceDtos;
import com.example.beyond_may_be.place.dto.PlaceDtos.PlaceRecommendationResponse;
import java.util.List;

public final class PlaceConverter {
  private PlaceConverter() {}

  public static PlaceDtos.DetailResponse toDetailResponse(Place place) {
    return new PlaceDtos.DetailResponse(
        place.getId(),
        place.getName(),
        place.getCategory(),
        place.getTravelMbtiType(),
        List.copyOf(place.getTags()),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        place.getBusinessHours(),
        place.getDescription(),
        place.getThumbnailUrl());
  }

  public static PlaceRecommendationResponse toRecommendationResponse(List<Place> places) {
    return new PlaceRecommendationResponse(
        places.stream().map(PlaceConverter::toDetailResponse).toList());
  }

  public static PlaceDtos.NearbyPlaceResponse toNearbyPlaceResponse(
      Place place, long distanceMeters) {
    return new PlaceDtos.NearbyPlaceResponse(
        place.getId(),
        place.getName(),
        place.getCategory(),
        place.getLatitude(),
        place.getLongitude(),
        distanceMeters,
        place.getThumbnailUrl());
  }
}
