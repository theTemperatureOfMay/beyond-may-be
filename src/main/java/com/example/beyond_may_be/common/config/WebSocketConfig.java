package com.example.beyond_may_be.common.config;

import com.example.beyond_may_be.common.security.ExplorationLocationMessageInterceptor;
import com.example.beyond_may_be.common.security.StompAuthenticationInterceptor;
import com.example.beyond_may_be.common.websocket.StompContractErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthenticationInterceptor stompAuthenticationInterceptor;
  private final ExplorationLocationMessageInterceptor explorationLocationMessageInterceptor;
  private final StompContractErrorHandler stompContractErrorHandler;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.setErrorHandler(stompContractErrorHandler);
    registry.addEndpoint("/ws");
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        stompAuthenticationInterceptor, explorationLocationMessageInterceptor);
  }
}
