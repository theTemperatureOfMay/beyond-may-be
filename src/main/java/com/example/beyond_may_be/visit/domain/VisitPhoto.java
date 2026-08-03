package com.example.beyond_may_be.visit.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "visit_photos",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"visit_id", "display_order"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitPhoto extends BaseEntity {

  @Id
  @Column(name = "visit_photo_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "visit_id", nullable = false)
  private Long visitId;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;

  @Builder
  public VisitPhoto(Long visitId, String objectKey, Integer displayOrder) {
    this.visitId = visitId;
    this.objectKey = objectKey;
    this.displayOrder = displayOrder;
  }
}
