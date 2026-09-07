package com.example.beyond_may_be.visit.repository;

import com.example.beyond_may_be.visit.domain.VisitPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitPhotoRepository extends JpaRepository<VisitPhoto, Long> {

  @Query(
      "SELECT COALESCE(MAX(photo.displayOrder), 0) FROM VisitPhoto photo "
          + "WHERE photo.visitId = :visitId")
  int findMaxDisplayOrderByVisitId(@Param("visitId") Long visitId);

  List<VisitPhoto> findByVisitIdInOrderByVisitIdAscDisplayOrderAsc(List<Long> visitIds);
}
