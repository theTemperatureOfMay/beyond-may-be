package com.example.beyond_may_be.visit.repository;

import com.example.beyond_may_be.visit.domain.Visit;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT visit FROM Visit visit WHERE visit.id = :visitId")
  Optional<Visit> findByIdForUpdate(@Param("visitId") Long visitId);

  boolean existsByParticipantIdAndPlaceId(Long participantId, Long placeId);

  boolean existsByPlaceIdAndParticipantIdIn(Long placeId, List<Long> participantIds);

  List<Visit> findByParticipantIdIn(List<Long> participantIds);

  List<Visit> findByParticipantIdInOrderByVisitedAtDesc(List<Long> participantIds);

  @Query(
      "SELECT COUNT(DISTINCT v.coursePlaceId) FROM Visit v "
          + "WHERE v.participantId IN :participantIds AND v.coursePlaceId IS NOT NULL")
  long countDistinctCoursePlaceIds(@Param("participantIds") List<Long> participantIds);

  @Query(
      "SELECT v.participantId AS participantId, COUNT(DISTINCT v.placeId) AS visitCount "
          + "FROM Visit v WHERE v.participantId IN :participantIds GROUP BY v.participantId")
  List<ParticipantVisitCount> countByParticipantIds(
      @Param("participantIds") List<Long> participantIds);

  interface ParticipantVisitCount {
    Long getParticipantId();

    long getVisitCount();
  }
}
