package com.example.beyond_may_be.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class StompAuthenticationInterceptorTest {

  @InjectMocks private StompAuthenticationInterceptor interceptor;

  @Mock private AuthTokenService authTokenService;

  @ParameterizedTest
  @EnumSource(
      value = StompCommand.class,
      names = {"CONNECT", "STOMP"})
  void validBearerTokenAuthenticatesConnectFrame(StompCommand command) {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    StompHeaderAccessor headers = StompHeaderAccessor.create(command);
    headers.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

    Authentication authentication =
        (Authentication)
            MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class).getUser();
    assertThat(authentication.getPrincipal()).isEqualTo(7L);
  }

  @Test
  void invalidBearerTokenRejectsConnectFrame() {
    given(authTokenService.resolveUserId("invalid-token")).willReturn(Optional.empty());
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
    headers.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(BadCredentialsException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = StompCommand.class,
      names = {"SEND", "MESSAGE"})
  void messageFrameIsDeniedUntilFeatureDestinationsAreImplemented(StompCommand command) {
    StompHeaderAccessor headers = StompHeaderAccessor.create(command);
    headers.setDestination("/app/explorations/1/locations");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void subscribeFrameIsDeniedUntilFeatureDestinationsAreImplemented() {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    headers.setDestination("/topic/explorations/1/events");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }
}
