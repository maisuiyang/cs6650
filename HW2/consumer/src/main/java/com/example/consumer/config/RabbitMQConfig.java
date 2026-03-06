package com.example.consumer.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("!test")
public class RabbitMQConfig {

  private static final int ROOM_COUNT = 20;

  @Bean
  public Declarables declarables() {
    List<org.springframework.amqp.core.Declarable> list = new ArrayList<>(ROOM_COUNT);
    for (int i = 1; i <= ROOM_COUNT; i++) {
      list.add(new Queue("room." + i, true));
    }
    return new Declarables(list);
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(new SimpleMessageConverter());
    return factory;
  }
}
