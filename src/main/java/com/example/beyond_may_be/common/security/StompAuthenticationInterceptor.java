package com.example.beyond_may_be.common.security;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthTokenService authTokenService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    SimpMessageType messageType = accessor.getMessageType();
    if (messageType == SimpMessageType.MESSAGE || messageType == SimpMessageType.SUBSCRIBE) {
      throw new AccessDeniedException("아직 허용된 WebSocket 메시지 경로가 없습니다.");
    }
    if (messageType != SimpMessageType.CONNECT) {
      return message;
    }

    String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      throw new BadCredentialsException("WebSocket 인증에 실패했습니다.");
    }

    String token = authorization.substring(BEARER_PREFIX.length());
    Long userId =
        authTokenService
            .resolveUserId(token)
            .orElseThrow(() -> new BadCredentialsException("WebSocket 인증에 실패했습니다."));
    accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    return message;
  }
}
