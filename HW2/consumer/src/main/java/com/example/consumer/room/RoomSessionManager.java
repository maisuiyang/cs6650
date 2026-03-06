package com.example.consumer.room;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe room -> sessions. Broadcasts message to all sessions in a room.
 */
public class RoomSessionManager {

  private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

  public void addSession(String roomId, WebSocketSession session) {
    roomSessions.compute(roomId, (k, set) -> {
      Set<WebSocketSession> s = set != null ? set : ConcurrentHashMap.newKeySet();
      s.add(session);
      return s;
    });
  }

  public void removeSession(String roomId, WebSocketSession session) {
    roomSessions.computeIfPresent(roomId, (k, set) -> {
      set.remove(session);
      return set.isEmpty() ? null : set;
    });
  }

  public void broadcast(String roomId, String messageJson) {
    Set<WebSocketSession> sessions = roomSessions.get(roomId);
    if (sessions == null || sessions.isEmpty()) return;

    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        try {
          session.sendMessage(new TextMessage(messageJson));
        } catch (IOException e) {
          // continue to other sessions
        }
      }
    }
  }

  public int sessionCount(String roomId) {
    Set<WebSocketSession> set = roomSessions.get(roomId);
    return set == null ? 0 : set.size();
  }
}
