package com.example.chatserver.ws;

import com.example.chatserver.model.ChatMessage;
import com.example.chatserver.queue.QueuePublisher;
import com.example.chatserver.validation.MessageValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final ObjectMapper mapper = new ObjectMapper();
  private final QueuePublisher queuePublisher;

  public ChatWebSocketHandler(QueuePublisher queuePublisher) {
    this.queuePublisher = queuePublisher;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    System.out.println("OPEN session=" + session.getId() + " uri=" + session.getUri());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String raw = message.getPayload();

    if (raw == null || raw.trim().isEmpty()) {
      session.sendMessage(new TextMessage(
          "{\"status\":\"ERROR\",\"errorMessage\":\"Empty message\"}"
      ));
      return;
    }

    String roomId = extractRoomId(session);
    String clientIp = getClientIp(session);

    try {
      ChatMessage msg = mapper.readValue(raw, ChatMessage.class);
      MessageValidator.validate(msg);

      String messageId = queuePublisher.publish(roomId, msg, clientIp);

      ObjectNode ok = mapper.createObjectNode();
      ok.put("status", "OK");
      ok.put("serverTimestamp", Instant.now().toString());
      ok.put("roomId", roomId);
      ok.put("messageId", messageId);
      ok.set("originalMessage", mapper.valueToTree(msg));

      session.sendMessage(new TextMessage(ok.toString()));
    } catch (Exception e) {
      ObjectNode err = mapper.createObjectNode();
      err.put("status", "ERROR");
      err.put("errorMessage", e.getMessage());
      err.put("roomId", roomId);

      session.sendMessage(new TextMessage(err.toString()));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    System.out.println("CLOSE session=" + session.getId() + " status=" + status);
  }

  private String extractRoomId(WebSocketSession session) {
    if (session.getUri() == null) return "";
    String path = session.getUri().getPath();
    String[] parts = path.split("/");
    return parts.length == 0 ? "" : parts[parts.length - 1];
  }

  private String getClientIp(WebSocketSession session) {
    if (session.getRemoteAddress() != null) {
      return session.getRemoteAddress().getAddress().getHostAddress();
    }
    Map<String, Object> attrs = session.getAttributes();
    Object forwarded = attrs != null ? attrs.get("X-Forwarded-For") : null;
    return forwarded != null ? forwarded.toString() : "";
  }
}
