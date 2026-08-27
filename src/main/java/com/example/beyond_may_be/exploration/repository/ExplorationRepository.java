package com.example.beyond_may_be.exploration.repository;

import com.example.beyond_may_be.exploration.domain.Exploration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExplorationRepository extends JpaRepository<Exploration, Long> {
  Optional<Exploration> findByCourseId(Long courseId);
}
