package com.example.beyond_may_be.exploration.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "exploration_participants",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"exploration_id", "user_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplorationParticipant extends BaseEntity {

  @Id
  @Column(name = "exploration_participant_id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "exploration_id", nullable = false)
  private Long explorationId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ParticipantRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ParticipantStatus status;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "location_sharing_enabled", nullable = false)
  private boolean locationSharingEnabled;

  @Column(name = "joined_at", nullable = false)
  private LocalDateTime joinedAt;

  @Column(name = "left_at")
  private LocalDateTime leftAt;

  @Builder
  public ExplorationParticipant(
      Long explorationId,
      Long userId,
      ParticipantRole role,
      ParticipantStatus status,
      String displayName,
      boolean locationSharingEnabled,
      LocalDateTime joinedAt,
      LocalDateTime leftAt) {
    this.explorationId = explorationId;
    this.userId = userId;
    this.role = role;
    this.status = status;
    this.displayName = displayName;
    this.locationSharingEnabled = locationSharingEnabled;
    this.joinedAt = joinedAt;
    this.leftAt = leftAt;
  }
}
