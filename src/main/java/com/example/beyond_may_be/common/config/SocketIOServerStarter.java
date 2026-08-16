package com.example.beyond_may_be.common.config;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 이벤트 리스너를 등록하는 컴포넌트들의 초기화가 모두 끝난 뒤 서버를 시작하기 위해 {@link
 * ApplicationReadyEvent}(컨텍스트 전체 초기화 완료 시점)에서 start()를 호출한다.
 */
@Component
@RequiredArgsConstructor
public class SocketIOServerStarter {

  private final SocketIOServer socketIOServer;

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    socketIOServer.start();
  }
}
