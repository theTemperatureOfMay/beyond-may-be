package com.example.beyond_may_be.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.security.ExplorationLocationMessageInterceptor;
import com.example.beyond_may_be.common.security.StompAuthenticationInterceptor;
import com.example.beyond_may_be.common.websocket.StompContractErrorHandler;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.service.ExplorationLocationService;
import com.example.beyond_may_be.exploration.service.ExplorationStateEventPublisher;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
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
  @Autowired private ExplorationParticipantRepository explorationParticipantRepository;
  @Autowired private ExplorationLocationService locationService;
  @Autowired private ExplorationStateEventPublisher eventPublisher;
  @Autowired private SimpleBrokerMessageHandler brokerMessageHandler;
  @Autowired private StompContractErrorHandler stompContractErrorHandler;
  @Autowired private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"events", "visits", "locations"})
  void existingSubscriptionStopsReceivingAfterLeave(String topic) throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    var participant = activeParticipant();
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 7L))
        .willReturn(Optional.of(participant));
    var headers = new StompHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    var session = connect(headers).get(5, TimeUnit.SECONDS);
    String destination = "/topic/explorations/44/" + topic;
    var received = new java.util.concurrent.LinkedBlockingQueue<String>();
    session.subscribe(
        destination,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders ignored) {
            return byte[].class;
          }

          @Override
          public void handleFrame(StompHeaders ignored, Object payload) {
            received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
          }
        });
    awaitSubscription(destination);
    messagingTemplate.convertAndSend(destination, "before-leave");
    assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("before-leave");
    org.mockito.Mockito.clearInvocations(explorationParticipantRepository);

    participant.leave(LocalDateTime.now());
    messagingTemplate.convertAndSend(destination, "after-leave");

    org.mockito.Mockito.verify(explorationParticipantRepository, timeout(5000))
        .findByExplorationIdAndUserId(44L, 7L);
    assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
    session.disconnect();
  }

  @AfterEach
  void stopClient() {
    client.stop();
    reset(authTokenService, explorationParticipantRepository, locationService);
  }

  @Test
  void activeParticipantReceivesJsonExplorationStateEvent() throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 7L))
        .willReturn(Optional.of(activeParticipant()));
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    StompSession session = connect(connectHeaders).get(5, TimeUnit.SECONDS);
    CompletableFuture<Void> received = new CompletableFuture<>();
    AtomicReference<StompHeaders> receivedHeaders = new AtomicReference<>();
    AtomicReference<String> receivedPayload = new AtomicReference<>();
    StompHeaders subscribeHeaders = new StompHeaders();
    subscribeHeaders.setDestination("/topic/explorations/44/events");

    session.subscribe(
        subscribeHeaders,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            receivedHeaders.set(headers);
            receivedPayload.set(new String((byte[]) payload, StandardCharsets.UTF_8));
            received.complete(null);
          }
        });
    awaitStateEventSubscription();
    OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-15T21:15:00+09:00");

    eventPublisher.publish(
        new ExplorationDtos.ParticipantJoinedEvent(
            UUID.fromString("d8ed786d-7fce-4bb8-b174-3256a573603c"),
            "PARTICIPANT_JOINED",
            44L,
            occurredAt,
            new ExplorationDtos.ParticipantJoinedData(72L, "김감자감자 (2)", "MEMBER", 4)));

    received.get(5, TimeUnit.SECONDS);
    assertThat(receivedHeaders.get().getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
    assertThat(receivedPayload.get())
        .contains("\"eventType\":\"PARTICIPANT_JOINED\"")
        .contains("\"participantCount\":4");
    session.disconnect();
  }

  @Test
  void authenticatedUserSendsValidLocationFrame() throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    StompSession session = connect(connectHeaders).get(5, TimeUnit.SECONDS);
    String destination = "/topic/explorations/44/locations";

    session.subscribe(destination, new StompSessionHandlerAdapter() {});
    awaitSubscription(destination);
    StompHeaders sendHeaders = new StompHeaders();
    sendHeaders.setDestination("/app/explorations/44/locations");
    sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
    session.send(
        sendHeaders,
        """
        {
          "latitude": 35.1402,
          "longitude": 126.9124,
          "accuracyMeters": 18.5,
          "recordedAt": "2026-08-15T14:34:59+09:00"
        }
        """
            .getBytes(StandardCharsets.UTF_8));

    org.mockito.Mockito.verify(locationService, timeout(5_000)).validateSubscription(44L, 7L);
    org.mockito.Mockito.verify(locationService, timeout(5_000))
        .accept(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(44L),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(
                new ExplorationDtos.LocationUpdateRequest(
                    new BigDecimal("35.1402"),
                    new BigDecimal("126.9124"),
                    new BigDecimal("18.5"),
                    OffsetDateTime.parse("2026-08-15T14:34:59+09:00"))));
    session.disconnect();
  }

  @Test
  void excessiveLocationAccuracyReturnsStableStompErrorCode() throws Exception {
    ReceivedError error =
        sendLocationAndAwaitError(
            """
        {
          "latitude": 35.1402,
          "longitude": 126.9124,
          "accuracyMeters": 50.1,
          "recordedAt": "2026-08-15T14:34:59+09:00"
        }
        """);

    assertThat(error.headers().getFirst("message")).isEqualTo("LOCATION_ACCURACY_EXCEEDED");
    assertThat(error.payload()).isNullOrEmpty();
  }

  @Test
  void invalidLocationPayloadReturnsStableStompErrorCode() throws Exception {
    ReceivedError error =
        sendLocationAndAwaitError(
            """
            {
              "latitude": "35.1402",
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    assertThat(error.headers().getFirst("message")).isEqualTo("LOCATION_PAYLOAD_INVALID");
  }

  @Test
  void malformedLocationExplorationIdReturnsPayloadErrorCode() throws Exception {
    ReceivedError error =
        sendLocationAndAwaitError(
            "/app/explorations/not-a-number/locations",
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    assertThat(error.headers().getFirst("message")).isEqualTo("LOCATION_PAYLOAD_INVALID");
  }

  @Test
  void unexpectedLocationFailureReturnsStableStompErrorCode() throws Exception {
    org.mockito.Mockito.doThrow(new IllegalStateException("예상하지 못한 처리 실패"))
        .when(locationService)
        .accept(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(44L),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.any(ExplorationDtos.LocationUpdateRequest.class));

    ReceivedError error =
        sendLocationAndAwaitError(
            """
            {
              "latitude": 35.1402,
              "longitude": 126.9124,
              "accuracyMeters": 18.5,
              "recordedAt": "2026-08-15T14:34:59+09:00"
            }
            """);

    assertThat(error.headers().getFirst("message")).isEqualTo("LOCATION_PROCESSING_FAILED");
  }

  @Test
  void malformedExplorationSubscriptionReturnsStompError() throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    CompletableFuture<StompHeaders> errorFrame = new CompletableFuture<>();
    StompSessionHandlerAdapter sessionHandler =
        new StompSessionHandlerAdapter() {
          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            errorFrame.complete(headers);
          }
        };
    StompSession session = connect(connectHeaders, sessionHandler).get(5, TimeUnit.SECONDS);

    session.subscribe(
        "/topic/explorations/not-a-number/events", new StompSessionHandlerAdapter() {});

    assertThat(errorFrame.get(5, TimeUnit.SECONDS).getFirst("message")).isNotBlank();
  }

  @Test
  void nonSendLocationDestinationDoesNotBecomeLocationProcessingFailure() {
    StompHeaderAccessor clientHeaders = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    clientHeaders.setDestination("/app/explorations/44/locations");
    Message<byte[]> clientMessage =
        MessageBuilder.createMessage(new byte[0], clientHeaders.getMessageHeaders());

    Message<byte[]> errorMessage =
        stompContractErrorHandler.handleClientMessageProcessingError(
            clientMessage, new AccessDeniedException("구독 권한이 없습니다."));
    assertThat(errorMessage).isNotNull();
    StompHeaderAccessor errorHeaders =
        MessageHeaderAccessor.getAccessor(errorMessage, StompHeaderAccessor.class);

    assertThat(errorHeaders).isNotNull();
    assertThat(errorHeaders.getMessage()).isEqualTo("구독 권한이 없습니다.");
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
    return connect(connectHeaders, new StompSessionHandlerAdapter() {});
  }

  private CompletableFuture<StompSession> connect(
      StompHeaders connectHeaders, StompSessionHandlerAdapter sessionHandler) {
    return client.connectAsync(
        "ws://localhost:" + port + "/ws",
        new WebSocketHttpHeaders(),
        connectHeaders,
        sessionHandler);
  }

  private ReceivedError sendLocationAndAwaitError(String payload) throws Exception {
    return sendLocationAndAwaitError("/app/explorations/44/locations", payload);
  }

  private ReceivedError sendLocationAndAwaitError(String destination, String payload)
      throws Exception {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    CompletableFuture<ReceivedError> errorFrame = new CompletableFuture<>();
    StompSessionHandlerAdapter sessionHandler =
        new StompSessionHandlerAdapter() {
          @Override
          public void handleFrame(StompHeaders headers, Object framePayload) {
            errorFrame.complete(new ReceivedError(headers, (byte[]) framePayload));
          }
        };
    StompSession session = connect(connectHeaders, sessionHandler).get(5, TimeUnit.SECONDS);
    StompHeaders sendHeaders = new StompHeaders();
    sendHeaders.setDestination(destination);
    sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);

    session.send(sendHeaders, payload.getBytes(StandardCharsets.UTF_8));

    return errorFrame.get(5, TimeUnit.SECONDS);
  }

  private record ReceivedError(StompHeaders headers, byte[] payload) {}

  private ExplorationParticipant activeParticipant() {
    ExplorationParticipant participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(7L)
            .role(ParticipantRole.MEMBER)
            .status(ParticipantStatus.ACTIVE)
            .displayName("김감자감자")
            .locationSharingEnabled(false)
            .joinedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
            .build();
    ReflectionTestUtils.setField(participant, "id", 72L);
    return participant;
  }

  private void awaitStateEventSubscription() throws InterruptedException {
    awaitSubscription("/topic/explorations/44/events");
  }

  private void awaitSubscription(String destination) throws InterruptedException {
    SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
    headers.setDestination(destination);
    Message<byte[]> lookupMessage =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    for (int attempt = 0;
        attempt < 250
            && brokerMessageHandler
                .getSubscriptionRegistry()
                .findSubscriptions(lookupMessage)
                .isEmpty();
        attempt++) {
      TimeUnit.MILLISECONDS.sleep(20);
    }
    assertThat(brokerMessageHandler.getSubscriptionRegistry().findSubscriptions(lookupMessage))
        .isNotEmpty();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
      })
  @Import({
    SecurityConfig.class,
    WebSocketConfig.class,
    StompAuthenticationInterceptor.class,
    ExplorationLocationMessageInterceptor.class,
    StompContractErrorHandler.class,
    ExplorationStateEventPublisher.class
  })
  static class TestApplication {

    @Bean
    AuthTokenService authTokenService() {
      return mock(AuthTokenService.class);
    }

    @Bean
    ExplorationParticipantRepository explorationParticipantRepository() {
      return mock(ExplorationParticipantRepository.class);
    }

    @Bean
    ExplorationLocationService explorationLocationService() {
      return mock(ExplorationLocationService.class);
    }
  }
}
