package com.aimes.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

  private final NotificationWebSocketManager notificationWebSocketManager;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Long userId = (Long) session.getAttributes().get(NotificationHandshakeInterceptor.USER_ID_ATTR);
    if (userId != null) {
      notificationWebSocketManager.register(userId, session);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    notificationWebSocketManager.unregister(session);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    notificationWebSocketManager.unregister(session);
  }
}
