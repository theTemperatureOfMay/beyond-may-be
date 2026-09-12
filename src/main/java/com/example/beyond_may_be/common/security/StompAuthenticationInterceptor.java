package com.example.beyond_may_be.common.security;

import com.example.beyond_may_be.auth.service.AuthTokenService;
import com.example.beyond_may_be.common.websocket.StompContractException;
import com.example.beyond_may_be.common.websocket.StompContractException.Code;
import com.example.beyond_may_be.exploration.domain.enums.ParticipantStatus;
import com.example.beyond_may_be.exploration.repository.ExplorationParticipantRepository;
import com.example.beyond_may_be.exploration.service.ExplorationLocationService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final Pattern EXPLORATION_ACTIVE_PARTICIPANT_DESTINATION =
      Pattern.compile("/topic/explorations/(\\d+)/(?:events|visits)");
  private static final Pattern EXPLORATION_LOCATIONS_SEND_DESTINATION =
      Pattern.compile("/app/explorations/\\d+/locations");
  private static final Pattern EXPLORATION_LOCATIONS_SEND_LIKE_DESTINATION =
      Pattern.compile("/app/explorations/[^/]*/locations");
  private static final Pattern EXPLORATION_LOCATIONS_SUBSCRIBE_DESTINATION =
      Pattern.compile("/topic/explorations/(\\d+)/locations");

  private final AuthTokenService authTokenService;
  private final ExplorationParticipantRepository explorationParticipantRepository;
  private final ExplorationLocationService explorationLocationService;
  private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();

  public StompAuthenticationInterceptor(
      AuthTokenService authTokenService,
      ExplorationParticipantRepository explorationParticipantRepository,
      @Lazy ExplorationLocationService explorationLocationService) {
    this.authTokenService = authTokenService;
    this.explorationParticipantRepository = explorationParticipantRepository;
    this.explorationLocationService = explorationLocationService;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    SimpMessageType messageType = accessor.getMessageType();
    if (messageType == SimpMessageType.MESSAGE) {
      if (canSendLocationUpdate(accessor)) {
        return message;
      }
      String destination = String.valueOf(accessor.getDestination());
      if (accessor.getCommand() == StompCommand.SEND
          && EXPLORATION_LOCATIONS_SEND_LIKE_DESTINATION.matcher(destination).matches()
          && !EXPLORATION_LOCATIONS_SEND_DESTINATION.matcher(destination).matches()) {
        throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID);
      }
      throw new AccessDeniedException("아직 허용된 WebSocket 메시지 경로가 없습니다.");
    }
    if (messageType == SimpMessageType.SUBSCRIBE) {
      if (!canSubscribeToActiveParticipantTopic(accessor)
          && !canSubscribeToExplorationLocations(accessor)) {
        throw new AccessDeniedException("탐험 이벤트를 구독할 권한이 없습니다.");
      }
      return message;
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
    if (accessor.getSessionId() != null) {
      sessionUsers.put(accessor.getSessionId(), userId);
    }
    return message;
  }

  @EventListener
  public void handleDisconnect(SessionDisconnectEvent event) {
    sessionUsers.remove(event.getSessionId());
  }

  public Message<?> filterOutbound(Message<?> message) {
    // ponytail: 송신 대상마다 DB 권한을 확인한다. 처리량이 병목이면 이탈 즉시 무효화하는 구독 캐시로 교체한다.
    if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
      return message;
    }
    String destination =
        String.valueOf(SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
    Matcher matcher = EXPLORATION_ACTIVE_PARTICIPANT_DESTINATION.matcher(destination);
    boolean location = false;
    if (!matcher.matches()) {
      matcher = EXPLORATION_LOCATIONS_SUBSCRIBE_DESTINATION.matcher(destination);
      if (!matcher.matches()) {
        return message;
      }
      location = true;
    }
    String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
    Long userId = sessionId == null ? null : sessionUsers.get(sessionId);
    if (userId == null) {
      return null;
    }
    var participant =
        explorationParticipantRepository.findByExplorationIdAndUserId(
            Long.valueOf(matcher.group(1)), userId);
    if (participant.isEmpty()
        || participant.get().getStatus() == ParticipantStatus.LEFT
        || (location && participant.get().getStatus() != ParticipantStatus.ACTIVE)) {
      return null;
    }
    return message;
  }

  private boolean canSendLocationUpdate(StompHeaderAccessor accessor) {
    return accessor.getCommand() == StompCommand.SEND
        && EXPLORATION_LOCATIONS_SEND_DESTINATION
            .matcher(String.valueOf(accessor.getDestination()))
            .matches()
        && accessor.getUser() instanceof Authentication authentication
        && authentication.getPrincipal() instanceof Long;
  }

  private boolean canSubscribeToExplorationLocations(StompHeaderAccessor accessor) {
    Matcher destinationMatcher =
        EXPLORATION_LOCATIONS_SUBSCRIBE_DESTINATION.matcher(
            String.valueOf(accessor.getDestination()));
    if (!destinationMatcher.matches()
        || !(accessor.getUser() instanceof Authentication authentication)
        || !(authentication.getPrincipal() instanceof Long userId)) {
      return false;
    }
    try {
      explorationLocationService.validateSubscription(
          Long.valueOf(destinationMatcher.group(1)), userId);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private boolean canSubscribeToActiveParticipantTopic(StompHeaderAccessor accessor) {
    Matcher destinationMatcher =
        EXPLORATION_ACTIVE_PARTICIPANT_DESTINATION.matcher(
            String.valueOf(accessor.getDestination()));
    if (!destinationMatcher.matches()
        || !(accessor.getUser() instanceof Authentication authentication)
        || !(authentication.getPrincipal() instanceof Long userId)) {
      return false;
    }
    try {
      Long explorationId = Long.valueOf(destinationMatcher.group(1));
      return explorationParticipantRepository
          .findByExplorationIdAndUserId(explorationId, userId)
          .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
          .isPresent();
    } catch (NumberFormatException exception) {
      return false;
    }
  }
}
