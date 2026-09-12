package com.example.beyond_may_be.exploration.repository;

import com.example.beyond_may_be.exploration.domain.Exploration;
import com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExplorationRepository extends JpaRepository<Exploration, Long> {
  Optional<Exploration> findByCourseId(Long courseId);

  @Query(
      "SELECT e FROM Exploration e, ExplorationParticipant p "
          + "WHERE p.explorationId = e.id "
          + "AND p.userId = :userId "
          + "AND e.status = :status "
          + "AND (e.status <> com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.ONGOING "
          + "OR p.status = com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus.ACTIVE) "
          + "ORDER BY e.completedAt DESC")
  List<Exploration> findAllByParticipantUserIdAndStatus(
      @Param("userId") Long userId, @Param("status") ExplorationStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Exploration e where e.courseId = :courseId")
  Optional<Exploration> findByCourseIdForUpdate(@Param("courseId") Long courseId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Exploration e where e.id = :explorationId")
  Optional<Exploration> findByIdForUpdate(@Param("explorationId") Long explorationId);

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
