package com.example.chatserver.queue;

import com.example.chatserver.model.ChatMessage;
import com.example.chatserver.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QueuePublisher {

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final String exchangeName;
  private final String serverId;

  public QueuePublisher(
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      @Value("${chat.exchange.name:chat.exchange}") String exchangeName,
      @Value("${server.id:server-1}") String serverId) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.exchangeName = exchangeName;
    this.serverId = serverId;
  }

  public String publish(String roomId, ChatMessage msg, String clientIp) throws Exception {
    String messageId = UUID.randomUUID().toString();
    QueueMessage q = new QueueMessage();
    q.setMessageId(messageId);
    q.setRoomId(roomId);
    q.setUserId(msg.getUserId());
    q.setUsername(msg.getUsername());
    q.setMessage(msg.getMessage());
    q.setTimestamp(msg.getTimestamp());
    q.setMessageType(msg.getMessageType());
    q.setServerId(serverId);
    q.setClientIp(clientIp != null ? clientIp : "");

    String routingKey = "room." + roomId;
    byte[] body = objectMapper.writeValueAsBytes(q);
    rabbitTemplate.convertAndSend(exchangeName, routingKey, body);
    return messageId;
  }
}
