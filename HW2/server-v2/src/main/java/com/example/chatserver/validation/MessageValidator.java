package com.example.chatserver.validation;

import com.example.chatserver.model.ChatMessage;

import java.time.Instant;
import java.util.Set;

public class MessageValidator {

  private static final Set<String> TYPES = Set.of("TEXT", "JOIN", "LEAVE");

  public static void validate(ChatMessage msg) {
    if (msg == null) throw new IllegalArgumentException("Empty message");

    int uid;
    try {
      uid = Integer.parseInt(msg.getUserId());
    } catch (Exception e) {
      throw new IllegalArgumentException("userId must be an integer string");
    }
    if (uid < 1 || uid > 100000) throw new IllegalArgumentException("userId out of range (1-100000)");

    if (msg.getUsername() == null || !msg.getUsername().matches("^[a-zA-Z0-9]{3,20}$")) {
      throw new IllegalArgumentException("username must be 3-20 alphanumeric characters");
    }

    if (msg.getMessage() == null || msg.getMessage().length() < 1 || msg.getMessage().length() > 500) {
      throw new IllegalArgumentException("message must be 1-500 characters");
    }

    try {
      Instant.parse(msg.getTimestamp());
    } catch (Exception e) {
      throw new IllegalArgumentException("timestamp must be ISO-8601 (e.g., 2026-02-07T20:00:00Z)");
    }

    if (msg.getMessageType() == null || !TYPES.contains(msg.getMessageType())) {
      throw new IllegalArgumentException("messageType must be TEXT|JOIN|LEAVE");
    }
  }
}
