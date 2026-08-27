package com.example.beyond_may_be.exploration.repository;

import com.example.beyond_may_be.exploration.domain.Exploration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExplorationRepository extends JpaRepository<Exploration, Long> {
  Optional<Exploration> findByCourseId(Long courseId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Exploration e "
          + "SET e.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.ONGOING, "
          + "e.startedByParticipantId = :participantId, "
          + "e.startedAt = :startedAt, "
          + "e.updatedAt = :startedAt "
          + "WHERE e.id = :explorationId "
          + "AND e.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.BEFORE")
  int startIfBefore(
      @Param("explorationId") Long explorationId,
      @Param("participantId") Long participantId,
      @Param("startedAt") LocalDateTime startedAt);
}
