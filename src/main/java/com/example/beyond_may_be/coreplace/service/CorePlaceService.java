package com.example.beyond_may_be.coreplace.service;

import com.example.beyond_may_be.coreplace.converter.CorePlaceConverter;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.NearbyPlacesResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.RecommendationsResponse;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchRequest;
import com.example.beyond_may_be.coreplace.dto.CorePlaceDtos.SearchResponse;
import org.springframework.stereotype.Service;

@Service
public class CorePlaceService {

  public RecommendationsResponse getRecommendations(RecommendationsRequest request) {
    int recommendationBatchSize = request.limit() == null ? 10 : request.limit();
    return CorePlaceConverter.toRecommendationsResponse(
        request.scheduleId(), recommendationBatchSize);
  }

  public SearchResponse searchPlaces(SearchRequest request) {
    return CorePlaceConverter.toSearchResponse();
  }

  public NearbyPlacesResponse getNearbyPlaces(
      Long explorationId, double latitude, double longitude) {
    return CorePlaceConverter.toNearbyPlacesResponse();
  }
}
