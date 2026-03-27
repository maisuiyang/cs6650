package com.example.consumer.persistence;

import com.example.consumer.model.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
public class MessageDlqPublisher {

  private static final Logger log = LoggerFactory.getLogger(MessageDlqPublisher.class);

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;
  private final String dlqQueueName;

  public MessageDlqPublisher(
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      @Value("${chat.persistence.dlq-queue:db.write.dlq}") String dlqQueueName) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.dlqQueueName = dlqQueueName;
  }

  public void publishFailedBatch(List<QueueMessage> batch, String reason) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("reason", reason);
      payload.put("messages", batch);
      byte[] body = objectMapper.writeValueAsBytes(payload);
      MessageProperties props = new MessageProperties();
      props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
      rabbitTemplate.send("", dlqQueueName, new Message(body, props));
    } catch (Exception e) {
      log.error("Failed to publish to DLQ: {}", e.getMessage(), e);
    }
  }
}
