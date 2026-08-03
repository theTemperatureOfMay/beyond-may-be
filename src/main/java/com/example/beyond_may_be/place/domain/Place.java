package com.example.beyond_may_be.place.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.preference.domain.enums.TravelPreferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity {

  @Id
  @Column(name = "place_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String category;

  @Enumerated(EnumType.STRING)
  @Column(name = "travel_mbti_type", nullable = false)
  private TravelPreferenceType travelMbtiType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<String> tags;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false)
  private BigDecimal latitude;

  @Column(nullable = false)
  private BigDecimal longitude;

  @Column(name = "business_hours", nullable = false)
  private String businessHours;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "thumbnail_url")
  private String thumbnailUrl;

  @Column(nullable = false)
  private boolean active;

  @Builder
  public Place(
      String name,
      String category,
      TravelPreferenceType travelMbtiType,
      List<String> tags,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      String businessHours,
      String description,
      String thumbnailUrl,
      boolean active) {
    this.name = name;
    this.category = category;
    this.travelMbtiType = travelMbtiType;
    this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.businessHours = businessHours;
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
    this.active = active;
  }
}
