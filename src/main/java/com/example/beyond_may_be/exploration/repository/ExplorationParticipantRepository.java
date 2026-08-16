package com.example.beyond_may_be.exploration.repository;

import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExplorationParticipantRepository
    extends JpaRepository<ExplorationParticipant, Long> {

  Optional<ExplorationParticipant> findByExplorationIdAndUserId(Long explorationId, Long userId);

  List<ExplorationParticipant> findByExplorationId(Long explorationId);

  List<ExplorationParticipant> findByExplorationIdAndStatus(
      Long explorationId, ParticipantStatus status);

  @Query("SELECT p.id FROM ExplorationParticipant p WHERE p.explorationId = :explorationId")
  List<Long> findParticipantIdsByExplorationId(@Param("explorationId") Long explorationId);

  @Query(
      "SELECT COUNT(p) > 0 FROM ExplorationParticipant p, Exploration e "
          + "WHERE e.id = p.explorationId "
          + "AND p.userId = :userId "
          + "AND p.status = com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus.ACTIVE "
          + "AND e.status IN (com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.BEFORE, "
          + "com.example.beyond_may_be.exploration.domain.enums.ExplorationStatus.ONGOING) "
          + "AND p.explorationId <> :excludeExplorationId")
  boolean existsActiveParticipationElsewhere(
      @Param("userId") Long userId, @Param("excludeExplorationId") Long excludeExplorationId);
}
