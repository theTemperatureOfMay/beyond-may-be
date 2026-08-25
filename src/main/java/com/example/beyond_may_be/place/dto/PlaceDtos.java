package com.example.beyond_may_be.place.dto;

import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.math.BigDecimal;
import java.util.List;

public final class PlaceDtos {
  private PlaceDtos() {}

  public record PlaceRecommendationResponse(List<PlaceResponse> places) {}

  public record PlaceResponse(
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
}
