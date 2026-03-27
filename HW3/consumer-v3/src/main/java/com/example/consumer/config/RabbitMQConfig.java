package com.example.consumer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("!test")
public class RabbitMQConfig {

  private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);
  private static final int ROOM_COUNT = 20;

  @Bean
  public Declarables declarables(@Value("${chat.persistence.dlq-queue:db.write.dlq}") String dlqQueueName) {
    List<org.springframework.amqp.core.Declarable> list = new ArrayList<>(ROOM_COUNT + 1);
    for (int i = 1; i <= ROOM_COUNT; i++) {
      list.add(new Queue("room." + i, true));
    }
    list.add(new Queue(dlqQueueName, true));
    return new Declarables(list);
  }

  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(new SimpleMessageConverter());
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    return factory;
  }

  /**
   * Registers connection lifecycle logging. Spring AMQP's {@link CachingConnectionFactory}
   * automatically reconnects when the broker comes back; this makes that behavior visible.
   */
  @Bean
  SmartInitializingSingleton rabbitConnectionRecoveryLogger(CachingConnectionFactory connectionFactory) {
    return () -> connectionFactory.addConnectionListener(new ConnectionListener() {
      @Override
      public void onCreate(Connection connection) {
        log.info("RabbitMQ connection established (automatic recovery enabled)");
      }

      @Override
      public void onClose(Connection connection) {
        log.warn("RabbitMQ connection closed; Spring AMQP will reconnect using recovery-interval");
      }
    });
  }
}
