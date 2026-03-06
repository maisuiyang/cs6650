package com.example.chatserver.queue;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RabbitMQConfig {

  public static final int ROOM_COUNT = 20;

  @Value("${chat.exchange.name:chat.exchange}")
  private String exchangeName;

  @Value("${chat.queue.prefix:room.}")
  private String queuePrefix;

  @Bean
  public TopicExchange chatExchange() {
    return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
  }

  @Bean
  public Declarables declarables(TopicExchange chatExchange) {
    List<Declarable> declarables = new ArrayList<>();
    for (int i = 1; i <= ROOM_COUNT; i++) {
      String queueName = queuePrefix + i;
      String routingKey = "room." + i;
      Queue queue = QueueBuilder.durable(queueName).build();
      Binding binding = BindingBuilder.bind(queue).to(chatExchange).with(routingKey);
      declarables.add(queue);
      declarables.add(binding);
    }
    return new Declarables(declarables);
  }
}
