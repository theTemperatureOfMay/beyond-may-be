package com.example.beyond_may_be.place.repository;

import com.example.beyond_may_be.place.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {}
