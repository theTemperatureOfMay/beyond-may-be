package com.example.beyond_may_be.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.security.StompAuthenticationInterceptor;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
    classes = WebSocketConfigIntegrationTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConfigIntegrationTest {

  private final WebSocketStompClient client =
      new WebSocketStompClient(new StandardWebSocketClient());

  @LocalServerPort private int port;

  @Autowired private AuthTokenService authTokenService;

  @AfterEach
  void stopClient() {
    client.stop();
  }

  @Test
  void validStompBearerTokenConnectsToWebSocketEndpoint() throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

    StompSession session = connect(connectHeaders).get(5, TimeUnit.SECONDS);

    assertThat(session.isConnected()).isTrue();
    session.disconnect();
  }

  @Test
  void invalidStompBearerTokenDoesNotConnect() {
    given(authTokenService.resolveUserId("invalid-token")).willReturn(Optional.empty());
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");

    assertThatThrownBy(() -> connect(connectHeaders).get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void crossOriginHandshakeDoesNotConnect() {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin("https://other.example");
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

    assertThatThrownBy(
            () ->
                client
                    .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        handshakeHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  private CompletableFuture<StompSession> connect(StompHeaders connectHeaders) {
    return client.connectAsync(
        "ws://localhost:" + port + "/ws",
        new WebSocketHttpHeaders(),
        connectHeaders,
        new StompSessionHandlerAdapter() {});
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({SecurityConfig.class, WebSocketConfig.class, StompAuthenticationInterceptor.class})
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return mock(AuthTokenService.class);
    }
  }
}
