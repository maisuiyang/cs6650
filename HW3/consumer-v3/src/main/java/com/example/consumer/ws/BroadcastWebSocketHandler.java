package com.example.consumer.ws;

import com.example.consumer.room.RoomSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class BroadcastWebSocketHandler extends TextWebSocketHandler {

  private final RoomSessionManager roomSessionManager;

  public BroadcastWebSocketHandler(RoomSessionManager roomSessionManager) {
    this.roomSessionManager = roomSessionManager;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    String roomId = extractRoomId(session);
    roomSessionManager.addSession(roomId, session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = extractRoomId(session);
    roomSessionManager.removeSession(roomId, session);
  }

  private String extractRoomId(WebSocketSession session) {
    if (session.getUri() == null) return "";
    String path = session.getUri().getPath();
    String[] parts = path.split("/");
    return parts.length == 0 ? "" : parts[parts.length - 1];
  }
}
