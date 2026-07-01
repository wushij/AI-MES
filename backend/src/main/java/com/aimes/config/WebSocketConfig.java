package com.aimes.config;

import com.aimes.websocket.NotificationHandshakeInterceptor;
import com.aimes.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final NotificationWebSocketHandler notificationWebSocketHandler;
  private final NotificationHandshakeInterceptor notificationHandshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(notificationWebSocketHandler, "/ws/notifications")
        .addInterceptors(notificationHandshakeInterceptor)
        .setAllowedOriginPatterns("*");
  }
}
