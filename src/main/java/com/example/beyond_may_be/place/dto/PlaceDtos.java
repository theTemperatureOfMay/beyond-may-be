package com.example.beyond_may_be.place.dto;

import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;

public final class PlaceDtos {
  private PlaceDtos() {}

  public record DetailResponse(
      Long placeId,
      String name,
      String category,
      TravelPreferenceType travelMbtiType,
      List<String> tags,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String businessHours,
      String description,
      String thumbnailUrl) {}

  public record PlaceRecommendationResponse(List<DetailResponse> places) {}

  public record NearbyPlacesResponse(List<NearbyPlaceResponse> places) {}

  public record NearbyPlaceResponse(
      Long placeId,
      String name,
      String category,
      BigDecimal latitude,
      BigDecimal longitude,
      long distanceMeters,
      String thumbnailUrl) {}
}
