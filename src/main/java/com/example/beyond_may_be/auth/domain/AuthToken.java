package com.example.beyond_may_be.auth.domain;

import com.example.beyond_may_be.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "auth_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthToken extends BaseEntity {

  @Id
  @Column(name = "token", length = 64)
  private String token;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Builder
  public AuthToken(String token, Long userId, LocalDateTime expiresAt) {
    this.token = token;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(LocalDateTime now) {
    return expiresAt != null && expiresAt.isBefore(now);
  }
}
