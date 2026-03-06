package com.example.consumer.config;

import com.example.consumer.room.RoomSessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoomSessionManagerConfig {

  @Bean
  public RoomSessionManager roomSessionManager() {
    return new RoomSessionManager();
  }
}
