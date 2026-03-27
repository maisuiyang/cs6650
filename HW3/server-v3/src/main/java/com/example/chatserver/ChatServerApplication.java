package com.example.chatserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ChatServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ChatServerApplication.class, args);
  }
}
