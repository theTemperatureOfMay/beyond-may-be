package com.example.beyond_may_be.recommendation.repository;

import com.example.beyond_may_be.recommendation.domain.RecommendationSet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationSetRepository extends JpaRepository<RecommendationSet, Long> {
  Optional<RecommendationSet> findByUserId(Long userId);
}
