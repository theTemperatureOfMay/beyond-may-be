package com.example.beyond_may_be.place.repository;

import com.example.beyond_may_be.place.domain.Place;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
  List<Place> findByActiveTrue();

  List<Place> findByTravelMbtiTypeAndActiveTrue(TravelPreferenceType travelMbtiType);
}
