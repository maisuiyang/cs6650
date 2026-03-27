package com.example.consumer.queue;

import com.example.consumer.model.QueueMessage;
import com.example.consumer.persistence.BatchedMessagePersistenceWorker;
import com.example.consumer.room.RoomSessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile("!test")
public class RoomMessageListener {

  private static final Logger log = LoggerFactory.getLogger(RoomMessageListener.class);

  private final RoomSessionManager roomSessionManager;
  private final ObjectMapper objectMapper;
  private final CircuitBreaker circuitBreaker;
  private final BatchedMessagePersistenceWorker persistenceWorker;

  public RoomMessageListener(
      RoomSessionManager roomSessionManager,
      ObjectMapper objectMapper,
      CircuitBreakerRegistry circuitBreakerRegistry,
      BatchedMessagePersistenceWorker persistenceWorker) {
    this.roomSessionManager = roomSessionManager;
    this.objectMapper = objectMapper;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("roomMessages");
    this.persistenceWorker = persistenceWorker;
  }

  @RabbitListener(
      queues = {"room.1", "room.2", "room.3", "room.4", "room.5",
          "room.6", "room.7", "room.8", "room.9", "room.10",
          "room.11", "room.12", "room.13", "room.14", "room.15",
          "room.16", "room.17", "room.18", "room.19", "room.20"},
      containerFactory = "rabbitListenerContainerFactory")
  public void handleMessage(Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    boolean redelivered = message.getMessageProperties().isRedelivered();

    final QueueMessage msg;
    try {
      msg = objectMapper.readValue(message.getBody(), QueueMessage.class);
    } catch (JsonProcessingException e) {
      log.warn("Invalid JSON; nack without requeue: {}", e.getMessage());
      channel.basicNack(deliveryTag, false, false);
      return;
    }

    String roomId = msg.getRoomId();
    if (roomId == null || roomId.isBlank()) {
      log.warn("Missing roomId; nack without requeue");
      channel.basicNack(deliveryTag, false, false);
      return;
    }

    try {
      circuitBreaker.executeRunnable(
          () -> {
            try {
              String json = objectMapper.writeValueAsString(msg);
              roomSessionManager.broadcast(roomId, json);
            } catch (JsonProcessingException e) {
              throw new IllegalStateException(e);
            }
          });
      if (!persistenceWorker.enqueue(msg)) {
        log.warn("Persistence queue saturated; message broadcast only (roomId={})", roomId);
      }
      channel.basicAck(deliveryTag, false);
    } catch (CallNotPermittedException e) {
      log.warn("Circuit breaker OPEN; nack with requeue (deliveryTag={})", deliveryTag);
      channel.basicNack(deliveryTag, false, true);
    } catch (Exception e) {
      log.error("Broadcast/processing failed (redelivered={}): {}", redelivered, e.getMessage(), e);
      if (redelivered) {
        log.error("Second failure after RabbitMQ redelivery; nack without requeue");
        channel.basicNack(deliveryTag, false, false);
      } else {
        channel.basicNack(deliveryTag, false, true);
      }
    }
  }
}
