package com.example.consumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Profile("!test")
public class SchedulingConfiguration implements SchedulingConfigurer {

  private final ScheduledExecutorService statisticsScheduler;

  public SchedulingConfiguration(ScheduledExecutorService statisticsScheduler) {
    this.statisticsScheduler = statisticsScheduler;
  }

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.setScheduler(statisticsScheduler);
  }
}
