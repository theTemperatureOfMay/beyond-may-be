package com.example.beyond_may_be.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.websocket.StompContractException;
import com.example.beyond_may_be.common.websocket.StompContractException.Code;
import com.example.beyond_may_be.exploration.domain.ExplorationParticipant;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantRole;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.service.ExplorationLocationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
  @Mock private ExplorationParticipantRepository explorationParticipantRepository;
  @Mock private ExplorationLocationService explorationLocationService;

  @ParameterizedTest
  @ValueSource(strings = {"events", "visits", "locations"})
  void outboundDropsMessagesAfterParticipantLeaves(String topic) {
    given(authTokenService.resolveUserId("valid-token")).willReturn(Optional.of(7L));
    var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
    connect.setSessionId("session-7");
    connect.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
    connect.setLeaveMutable(true);
    interceptor.preSend(
        MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()),
        mock(MessageChannel.class));
    var participant =
        ExplorationParticipant.builder()
            .explorationId(44L)
            .userId(7L)
            .status(ParticipantStatus.ACTIVE)
            .role(ParticipantRole.MEMBER)
            .build();
    given(explorationParticipantRepository.findByExplorationIdAndUserId(44L, 7L))
        .willReturn(Optional.of(participant));
    var outbound = StompHeaderAccessor.create(StompCommand.MESSAGE);
    outbound.setSessionId("session-7");
    outbound.setDestination("/topic/explorations/44/" + topic);
    var message = MessageBuilder.createMessage(new byte[0], outbound.getMessageHeaders());
    assertThat(interceptor.filterOutbound(message)).isSameAs(message);

    participant.leave(LocalDateTime.now());

    assertThat(interceptor.filterOutbound(message)).isNull();
  }

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

  @Test
  void authenticatedUserCanSendLocationUpdate() {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/explorations/44/locations");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
  }

  @Test
  void serverMessageFrameRemainsDenied() {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
    headers.setDestination("/app/explorations/1/locations");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/app/explorations/1/locations/extra", "/app/explorations/1/events"})
  void otherSendDestinationsRemainDenied(String destination) {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination(destination);
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void malformedLocationExplorationIdIsPayloadError() {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/explorations/not-a-number/locations");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(Code.LOCATION_PAYLOAD_INVALID));
  }

  @Test
  void emptyLocationExplorationIdIsPayloadError() {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SEND);
    headers.setDestination("/app/explorations//locations");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOfSatisfying(
            StompContractException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(Code.LOCATION_PAYLOAD_INVALID));
  }

  @Test
  void activeParticipantCanSubscribeToExplorationStateEvents() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(1L, 7L))
        .willReturn(Optional.of(participant(ParticipantStatus.ACTIVE)));
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    headers.setDestination("/topic/explorations/1/events");
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());

    assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
  }

  @Test
  void activeParticipantCanSubscribeToVisitEvents() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(1L, 7L))
        .willReturn(Optional.of(participant(ParticipantStatus.ACTIVE)));
    Message<byte[]> message = subscribeMessage("/topic/explorations/1/visits");

    assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);
  }

  @Test
  void activeParticipantCanSubscribeToExplorationLocations() {
    Message<byte[]> message = subscribeMessage("/topic/explorations/44/locations");

    assertThat(interceptor.preSend(message, mock(MessageChannel.class))).isSameAs(message);

    org.mockito.BDDMockito.then(explorationLocationService).should().validateSubscription(44L, 7L);
  }

  @ParameterizedTest
  @EnumSource(
      value = ParticipantStatus.class,
      names = {"LEFT", "COMPLETED"})
  void inactiveParticipantCannotSubscribeToExplorationStateEvents(ParticipantStatus status) {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(1L, 7L))
        .willReturn(Optional.of(participant(status)));
    Message<byte[]> message = subscribeMessage("/topic/explorations/1/events");

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void missingParticipantCannotSubscribeToExplorationStateEvents() {
    given(explorationParticipantRepository.findByExplorationIdAndUserId(1L, 7L))
        .willReturn(Optional.empty());
    Message<byte[]> message = subscribeMessage("/topic/explorations/1/events");

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/topic/explorations/not-a-number/events",
        "/topic/explorations/not-a-number/visits",
        "/topic/explorations/not-a-number/locations",
        "/topic/explorations/1/events/extra",
        "/topic/explorations/1/visits/extra"
      })
  void otherSubscriptionsRemainDenied(String destination) {
    Message<byte[]> message = subscribeMessage(destination);

    assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
        .isInstanceOf(AccessDeniedException.class);
  }

  private Message<byte[]> subscribeMessage(String destination) {
    StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    headers.setDestination(destination);
    headers.setUser(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    headers.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
  }

  private ExplorationParticipant participant(ParticipantStatus status) {
    return ExplorationParticipant.builder()
        .explorationId(1L)
        .userId(7L)
        .role(ParticipantRole.MEMBER)
        .status(status)
        .displayName("별밤지기")
        .locationSharingEnabled(false)
        .joinedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
        .build();
  }
}
