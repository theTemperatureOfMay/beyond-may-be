package com.example.beyond_may_be.common.security;

import com.example.beyond_may_be.common.websocket.StompContractException;
import com.example.beyond_may_be.common.websocket.StompContractException.Code;
import com.example.beyond_may_be.exploration.dto.ExplorationDtos;
import com.example.beyond_may_be.exploration.service.ExplorationLocationService;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ExplorationLocationMessageInterceptor implements ChannelInterceptor {

  private static final Pattern DESTINATION = Pattern.compile("/app/explorations/(\\d+)/locations");
  private static final BigDecimal MAX_ACCURACY_METERS = new BigDecimal("50");

  private final ObjectMapper objectMapper;
  private final Validator validator;
  private final ExplorationLocationService locationService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() != StompCommand.SEND) {
      return message;
    }
    Matcher matcher = DESTINATION.matcher(String.valueOf(accessor.getDestination()));
    if (!matcher.matches()) {
      return message;
    }
    if (!(accessor.getUser() instanceof Authentication authentication)
        || !(authentication.getPrincipal() instanceof Long userId)) {
      throw new AccessDeniedException("WebSocket 인증이 필요합니다.");
    }
    if (accessor.getSessionId() == null) {
      throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID);
    }
    if (accessor.getContentType() == null
        || !MimeTypeUtils.APPLICATION_JSON.isCompatibleWith(accessor.getContentType())) {
      throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID);
    }

    ExplorationDtos.LocationUpdateRequest request;
    try {
      request =
          objectMapper
              .readerFor(ExplorationDtos.LocationUpdateRequest.class)
              .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .readValue((byte[]) message.getPayload());
    } catch (JacksonException | ClassCastException exception) {
      throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID, exception);
    }
    if (request == null) {
      throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID);
    }
    if (request.accuracyMeters() != null
        && request.accuracyMeters().compareTo(MAX_ACCURACY_METERS) > 0) {
      throw new StompContractException(Code.LOCATION_ACCURACY_EXCEEDED);
    }
    if (!validator.validate(request).isEmpty()) {
      throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID);
    }

    Long explorationId;
    try {
      explorationId = Long.valueOf(matcher.group(1));
    } catch (NumberFormatException exception) {
      throw new StompContractException(Code.LOCATION_PAYLOAD_INVALID, exception);
    }
    locationService.accept(accessor.getSessionId(), explorationId, userId, request);
    return null;
  }
}
