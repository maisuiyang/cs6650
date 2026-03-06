package com.example.consumer.queue;

import com.example.consumer.model.QueueMessage;
import com.example.consumer.room.RoomSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!test")
public class RoomMessageListener {

  private static final int ROOM_COUNT = 20;

  private final RoomSessionManager roomSessionManager;
  private final ObjectMapper objectMapper;

  public RoomMessageListener(RoomSessionManager roomSessionManager, ObjectMapper objectMapper) {
    this.roomSessionManager = roomSessionManager;
    this.objectMapper = objectMapper;
  }

  @RabbitListener(queues = {"room.1", "room.2", "room.3", "room.4", "room.5",
      "room.6", "room.7", "room.8", "room.9", "room.10",
      "room.11", "room.12", "room.13", "room.14", "room.15",
      "room.16", "room.17", "room.18", "room.19", "room.20"})
  public void handleMessage(byte[] body) {
    try {
      QueueMessage msg = objectMapper.readValue(body, QueueMessage.class);
      String roomId = msg.getRoomId();
      if (roomId == null) return;
      String json = objectMapper.writeValueAsString(msg);
      roomSessionManager.broadcast(roomId, json);
    } catch (Exception e) {
      // log and skip
    }
  }
}
