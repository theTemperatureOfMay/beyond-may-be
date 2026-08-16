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

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE Exploration e SET "
          + "e.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.ONGOING, "
          + "e.startedAt = :startedAt, e.startedByParticipantId = :participantId "
          + "WHERE e.id = :id AND e.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.BEFORE")
  int startIfBefore(
      @Param("id") Long id,
      @Param("participantId") Long participantId,
      @Param("startedAt") LocalDateTime startedAt);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE Exploration e SET "
          + "e.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.COMPLETED, "
          + "e.completedAt = :completedAt "
          + "WHERE e.id = :id AND e.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.ONGOING")
  int completeIfOngoing(@Param("id") Long id, @Param("completedAt") LocalDateTime completedAt);
}
