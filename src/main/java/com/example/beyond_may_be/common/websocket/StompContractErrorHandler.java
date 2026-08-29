package com.example.beyond_may_be.common.websocket;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Component
@Slf4j
public class StompContractErrorHandler extends StompSubProtocolErrorHandler {

  private static final Pattern LOCATION_DESTINATION =
      Pattern.compile("/app/explorations/[^/]*/locations");

  @Override
  public @Nullable Message<byte[]> handleClientMessageProcessingError(
      @Nullable Message<byte[]> clientMessage, Throwable exception) {
    String code = findContractCode(exception);
    if (code == null && isLocationMessage(clientMessage)) {
      log.error("위치 메시지 처리 중 예상하지 못한 오류가 발생했습니다.", exception);
      code = StompContractException.Code.LOCATION_PROCESSING_FAILED.name();
    }
    return super.handleClientMessageProcessingError(
        clientMessage, code == null ? exception : new RuntimeException(code, exception));
  }

  private String findContractCode(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof StompContractException contractException) {
        return contractException.getCode().name();
      }
      if (current == current.getCause()) {
        break;
      }
    }
    return null;
  }

  private boolean isLocationMessage(@Nullable Message<byte[]> clientMessage) {
    if (clientMessage == null) {
      return false;
    }
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
    return accessor != null
        && accessor.getCommand() == StompCommand.SEND
        && LOCATION_DESTINATION.matcher(String.valueOf(accessor.getDestination())).matches();
  }
}
