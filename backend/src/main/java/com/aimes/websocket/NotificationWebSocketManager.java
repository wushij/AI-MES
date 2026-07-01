package com.aimes.websocket;

import com.aimes.entity.SysNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketManager {

  private final ObjectMapper objectMapper;

  private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

  public void register(Long userId, WebSocketSession session) {
    userSessions.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(session);
  }

  public void unregister(WebSocketSession session) {
    userSessions.values().forEach(sessions -> sessions.remove(session));
    userSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
  }

  public void pushToUser(Long userId, SysNotification notification) {
    Set<WebSocketSession> sessions = userSessions.get(userId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }

    String payload;
    try {
      payload = objectMapper.writeValueAsString(Map.of(
          "type", "notification",
          "data", notification));
    } catch (JsonProcessingException e) {
      log.warn("序列化通知消息失败: userId={}", userId, e);
      return;
    }

    TextMessage message = new TextMessage(payload);
    for (WebSocketSession session : sessions) {
      if (!session.isOpen()) {
        unregister(session);
        continue;
      }
      try {
        session.sendMessage(message);
      } catch (IOException e) {
        log.debug("推送通知失败，移除会话: sessionId={}", session.getId(), e);
        unregister(session);
      }
    }
  }
}
