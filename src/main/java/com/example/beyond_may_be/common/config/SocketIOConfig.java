package com.example.beyond_may_be.common.config;

import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.beyond_may_be.auth.service.AuthTokenService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * netty-socketio는 socket.io-client의 {@code auth} 옵션 payload(엔진 연결 후 CONNECT 패킷 body)를 읽지 못하고 HTTP
 * 핸드셰이크 레벨의 헤더·쿼리 파라미터만 본다. 그래서 토큰은 handshake 쿼리 파라미터({@code ?token=...})로 전달받는다.
 */
@Configuration
@RequiredArgsConstructor
public class SocketIOConfig {

  private static final String TOKEN_PARAM = "token";
  private static final String USER_ID_STORE_KEY = "userId";

  private final AuthTokenService authTokenService;

  @Value("${socketio.host}")
  private String host;

  @Value("${socketio.port}")
  private int port;

  @Bean(destroyMethod = "stop")
  public SocketIOServer socketIOServer() {
    com.corundumstudio.socketio.Configuration configuration =
        new com.corundumstudio.socketio.Configuration();
    configuration.setHostname(host);
    configuration.setPort(port);
    configuration.setAuthorizationListener(this::authorize);
    return new SocketIOServer(configuration);
  }

  private AuthorizationResult authorize(HandshakeData handshakeData) {
    String token = handshakeData.getSingleUrlParam(TOKEN_PARAM);
    if (token == null) {
      return AuthorizationResult.FAILED_AUTHORIZATION;
    }
    return authTokenService
        .resolveUserId(token)
        .<AuthorizationResult>map(
            userId -> new AuthorizationResult(true, Map.of(USER_ID_STORE_KEY, userId)))
        .orElse(AuthorizationResult.FAILED_AUTHORIZATION);
  }
}
