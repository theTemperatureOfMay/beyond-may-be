package com.example.beyond_may_be.user.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.mbti.domain.Mbti;
import com.example.beyond_may_be.mbti.domain.enums.MbtiType;
import com.example.beyond_may_be.team.domain.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Id
  @Column(name = "user_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "t_mbti")
  private MbtiType tMbti;

  @Column(name = "thinker_percentage")
  private Long thinkerPercentage;

  @Column(name = "foodie_percentage")
  private Long foodiePercentage;

  @Column(name = "artist_percentage")
  private Long artistPercentage;

  @Column(name = "rememberer_percentage")
  private Long remembererPercentage;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mbti_id", nullable = false)
  private Mbti mbti;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id", nullable = false)
  private Team team;
}
