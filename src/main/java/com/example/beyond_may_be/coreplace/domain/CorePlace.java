package com.example.beyond_may_be.coreplace.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "core_places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorePlace extends BaseEntity {

  @Id
  @Column(name = "place_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "place_img")
  private String placeImg;

  @Column(name = "place_intro", length = 200)
  private String placeIntro;

  @Column(name = "lat", nullable = false)
  private Integer latitude;

  @Column(name = "long", nullable = false)
  private Integer longitude;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false)
  private String category;
}
