package com.example.beyond_may_be.auth.service;

import com.example.beyond_may_be.auth.domain.AuthToken;
import com.example.beyond_may_be.auth.repository.AuthTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthTokenService {

  private static final long TOKEN_VALID_DAYS = 30;

  private final AuthTokenRepository authTokenRepository;

  public String issue(Long userId) {
    AuthToken authToken =
        AuthToken.builder()
            .token(UUID.randomUUID().toString())
            .userId(userId)
            .expiresAt(LocalDateTime.now().plusDays(TOKEN_VALID_DAYS))
            .build();
    return authTokenRepository.save(authToken).getToken();
  }

  @Transactional(readOnly = true)
  public Optional<Long> resolveUserId(String token) {
    return authTokenRepository
        .findByToken(token)
        .filter(authToken -> !authToken.isExpired(LocalDateTime.now()))
        .map(AuthToken::getUserId);
  }

  public void revoke(String token) {
    authTokenRepository.deleteById(token);
  }
}
