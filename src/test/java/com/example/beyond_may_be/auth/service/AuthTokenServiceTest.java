package com.example.beyond_may_be.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.beyond_may_be.auth.domain.AuthToken;
import com.example.beyond_may_be.auth.repository.AuthTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

  @InjectMocks private AuthTokenService authTokenService;

  @Mock private AuthTokenRepository authTokenRepository;

  @DisplayName("토큰을 발급하면 만료 시각이 함께 저장된다.")
  @Test
  void issue_savesTokenWithExpiry() {
    // given
    given(authTokenRepository.save(any(AuthToken.class))).willAnswer(inv -> inv.getArgument(0));

    // when
    String token = authTokenService.issue(1L);

    // then
    assertThat(token).isNotBlank();
    ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
    org.mockito.Mockito.verify(authTokenRepository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getToken()).isEqualTo(token);
    assertThat(captor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
  }

  @DisplayName("유효한 토큰이면 사용자 ID를 반환한다.")
  @Test
  void resolveUserId_validToken_returnsUserId() {
    // given
    AuthToken authToken =
        AuthToken.builder()
            .token("valid-token")
            .userId(7L)
            .expiresAt(LocalDateTime.now().plusDays(1))
            .build();
    given(authTokenRepository.findByToken("valid-token")).willReturn(Optional.of(authToken));

    // when
    Optional<Long> userId = authTokenService.resolveUserId("valid-token");

    // then
    assertThat(userId).contains(7L);
  }

  @DisplayName("존재하지 않는 토큰이면 비어있는 결과를 반환한다.")
  @Test
  void resolveUserId_unknownToken_returnsEmpty() {
    // given
    given(authTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

    // when
    Optional<Long> userId = authTokenService.resolveUserId("unknown");

    // then
    assertThat(userId).isEmpty();
  }

  @DisplayName("만료된 토큰이면 비어있는 결과를 반환한다.")
  @Test
  void resolveUserId_expiredToken_returnsEmpty() {
    // given
    AuthToken expired =
        AuthToken.builder()
            .token("expired-token")
            .userId(7L)
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build();
    given(authTokenRepository.findByToken("expired-token")).willReturn(Optional.of(expired));

    // when
    Optional<Long> userId = authTokenService.resolveUserId("expired-token");

    // then
    assertThat(userId).isEmpty();
  }
}
