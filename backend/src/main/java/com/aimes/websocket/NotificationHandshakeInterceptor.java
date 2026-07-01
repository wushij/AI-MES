package com.aimes.websocket;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {

  public static final String USER_ID_ATTR = "userId";

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }

    String token = servletRequest.getServletRequest().getParameter("token");
    if (token == null || token.isBlank()) {
      return false;
    }

    try {
      StpUtil.setTokenValue(token);
      StpUtil.checkLogin();
      attributes.put(USER_ID_ATTR, StpUtil.getLoginIdAsLong());
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // no-op
  }
}
