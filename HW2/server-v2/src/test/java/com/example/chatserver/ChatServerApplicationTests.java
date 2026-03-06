package com.example.chatserver;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "spring.rabbitmq.host=localhost")
@SpringBootTest
class ChatServerApplicationTests {

  @MockBean
  RabbitTemplate rabbitTemplate;

  @Test
  void contextLoads() {
  }
}
