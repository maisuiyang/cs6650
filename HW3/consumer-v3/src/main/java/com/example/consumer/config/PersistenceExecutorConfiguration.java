package com.example.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Profile("!test")
public class PersistenceExecutorConfiguration {

  @Bean(destroyMethod = "shutdown")
  public ExecutorService databaseWriterExecutor() {
    return Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "database-writer");
      t.setDaemon(true);
      return t;
    });
  }

  @Bean(destroyMethod = "shutdown")
  public ScheduledExecutorService statisticsScheduler() {
    return Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "statistics-aggregator");
      t.setDaemon(true);
      return t;
    });
  }
}
