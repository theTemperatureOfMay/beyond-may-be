package com.example.beyond_may_be.user.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "users",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"nickname", "identification_code"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Id
  @Column(name = "user_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String nickname;

  @Column(name = "identification_code", nullable = false)
  private Integer identificationCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "preference_type")
  private TravelPreferenceType preferenceType;

  @Column(name = "thinker_score")
  private Integer thinkerScore;

  @Column(name = "foodie_score")
  private Integer foodieScore;

  @Column(name = "artist_score")
  private Integer artistScore;

  @Column(name = "rememberer_score")
  private Integer remembererScore;

  public User(String nickname, Integer identificationCode) {
    this(nickname, identificationCode, null, null, null, null, null);
  }

  @Builder
  public User(
      String nickname,
      Integer identificationCode,
      TravelPreferenceType preferenceType,
      Integer thinkerScore,
      Integer foodieScore,
      Integer artistScore,
      Integer remembererScore) {
    this.nickname = nickname;
    this.identificationCode = identificationCode;
    this.preferenceType = preferenceType;
    this.thinkerScore = thinkerScore;
    this.foodieScore = foodieScore;
    this.artistScore = artistScore;
    this.remembererScore = remembererScore;
  }
}
