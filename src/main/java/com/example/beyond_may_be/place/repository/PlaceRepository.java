package com.example.beyond_may_be.place.repository;

import com.example.beyond_may_be.place.domain.Place;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PlaceRepository extends JpaRepository<Place, Long> {
  List<Place> findAllByActiveTrue();

  Optional<Place> findByIdAndActiveTrue(Long id);

  @Query("select p.tourContentId from Place p where p.tourContentId in :contentIds")
  Set<Long> findExistingTourContentIds(@Param("contentIds") Collection<Long> contentIds);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO places (
              created_at, updated_at, active, address, business_hours, category, description,
              latitude, longitude, name, tags, thumbnail_url, travel_mbti_type,
              tour_content_id, tour_content_type_id
          ) VALUES (
              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true, :address, NULL, :category, NULL,
              :latitude, :longitude, :name, CAST(:tags AS jsonb), :thumbnailUrl, :travelMbtiType,
              :tourContentId, :tourContentTypeId
          )
          ON CONFLICT (tour_content_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertTourApiPlace(
      @Param("tourContentId") Long tourContentId,
      @Param("tourContentTypeId") Integer tourContentTypeId,
      @Param("name") String name,
      @Param("category") String category,
      @Param("travelMbtiType") String travelMbtiType,
      @Param("tags") String tags,
      @Param("address") String address,
      @Param("latitude") BigDecimal latitude,
      @Param("longitude") BigDecimal longitude,
      @Param("thumbnailUrl") String thumbnailUrl);

  // 7-5. 외부 조회 중 값이 채워졌더라도 덮어쓰지 않도록 UPDATE 시점에 빈 값인지 다시 확인한다.
  @Modifying
  @Transactional
  @Query(
      value =
          """
          UPDATE places
          SET description = :description, updated_at = CURRENT_TIMESTAMP
          WHERE place_id = :placeId
            AND (description IS NULL OR BTRIM(description) = '')
          """,
      nativeQuery = true)
  int updateDescriptionIfMissing(
      @Param("placeId") Long placeId, @Param("description") String description);

  @Modifying
  @Transactional
  @Query(
      value =
          """
          UPDATE places
          SET business_hours = :businessHours, updated_at = CURRENT_TIMESTAMP
          WHERE place_id = :placeId
            AND (business_hours IS NULL OR BTRIM(business_hours) = '')
          """,
      nativeQuery = true)
  int updateBusinessHoursIfMissing(
      @Param("placeId") Long placeId, @Param("businessHours") String businessHours);
}
