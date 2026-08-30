package com.example.beyond_may_be.exploration.repository;

import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExplorationParticipantRepository
    extends JpaRepository<ExplorationParticipant, Long> {

  Optional<ExplorationParticipant> findByExplorationIdAndUserId(Long explorationId, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from ExplorationParticipant p "
          + "where p.explorationId = :explorationId and p.userId = :userId")
  Optional<ExplorationParticipant> findByExplorationIdAndUserIdForUpdate(
      @Param("explorationId") Long explorationId, @Param("userId") Long userId);

  List<ExplorationParticipant> findByExplorationId(Long explorationId);

  @Query(
      "SELECT COUNT(p) > 0 FROM ExplorationParticipant p, Exploration e "
          + "WHERE e.id = p.explorationId "
          + "AND p.userId = :userId "
          + "AND p.status = "
          + "com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus.ACTIVE "
          + "AND e.status IN ("
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.BEFORE, "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.ONGOING)")
  boolean existsActiveParticipation(@Param("userId") Long userId);
}
